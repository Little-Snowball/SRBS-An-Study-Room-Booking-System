package edu.campus.studyroom.attendance.service;

import edu.campus.studyroom.attendance.dto.AttendanceResult;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.IdempotencyService;
import edu.campus.studyroom.common.Ids;
import edu.campus.studyroom.reservation.domain.AttendanceEvent;
import edu.campus.studyroom.reservation.domain.AttendanceEventType;
import edu.campus.studyroom.reservation.domain.DeadlineRules;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.mapper.AttendanceEventMapper;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.resource.domain.RoomStatus;
import edu.campus.studyroom.resource.domain.StudyRoom;
import edu.campus.studyroom.resource.mapper.StudyRoomMapper;
import edu.campus.studyroom.rule.BusinessGuardLock;
import edu.campus.studyroom.rule.RuleService;
import edu.campus.studyroom.rule.domain.RuleConfig;
import edu.campus.studyroom.settlement.SettlementOutcome;
import edu.campus.studyroom.settlement.SettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Attendance commands: cancel / check-in / leave / return / check-out.
 * <p>
 * Every command follows the same protocol: guard row lock first, one trusted
 * {@code now}, in-transaction idempotency check, re-read FOR UPDATE, ownership
 * check (non-owner gets 404), deadline re-validation against the single clock,
 * attendance event + optimistic state update (affectedRows must be 1), dedup
 * record. Terminal writes for cancel/check-out belong to the settlement service;
 * commands that discover a passed deadline mid-flight settle first and then
 * reject, so a command can never bypass a due penalty.
 */
@Service
public class AttendanceCommandService {

    private static final String CMD_CANCEL = "RESERVATION_CANCEL";
    private static final String CMD_CHECK_IN = "RESERVATION_CHECK_IN";
    private static final String CMD_LEAVE = "RESERVATION_LEAVE";
    private static final String CMD_RETURN = "RESERVATION_RETURN";
    private static final String CMD_CHECK_OUT = "RESERVATION_CHECK_OUT";
    private static final String SETTLE_TRIGGER_COMMAND = "COMMAND";

    private final BusinessGuardLock businessGuardLock;
    private final BusinessClock clock;
    private final IdempotencyService idempotencyService;
    private final ReservationMapper reservationMapper;
    private final AttendanceEventMapper attendanceEventMapper;
    private final StudyRoomMapper studyRoomMapper;
    private final RuleService ruleService;
    private final SettlementService settlementService;

    public AttendanceCommandService(BusinessGuardLock businessGuardLock, BusinessClock clock,
                                    IdempotencyService idempotencyService,
                                    ReservationMapper reservationMapper,
                                    AttendanceEventMapper attendanceEventMapper,
                                    StudyRoomMapper studyRoomMapper, RuleService ruleService,
                                    SettlementService settlementService) {
        this.businessGuardLock = businessGuardLock;
        this.clock = clock;
        this.idempotencyService = idempotencyService;
        this.reservationMapper = reservationMapper;
        this.attendanceEventMapper = attendanceEventMapper;
        this.studyRoomMapper = studyRoomMapper;
        this.ruleService = ruleService;
        this.settlementService = settlementService;
    }

    /**
     * No-fault cancel inside the cancel window: CANCELLED with zero reward and
     * zero penalty. The terminal write (CANCEL event + settlement_result) is done
     * by the settlement service in this same transaction.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceResult cancel(Long reservationId, Long userId, String requestKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        String hash = Ids.requestHash(CMD_CANCEL + ":" + reservationId);
        idempotencyService.checkInTransaction(userId, CMD_CANCEL, requestKey, hash);

        Reservation reservation = loadOwnedForUpdate(reservationId, userId);

        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw BusinessException.invalidState(terminalHint(reservation, "订单当前状态不可取消"));
        }
        if (now.isAfter(reservation.getCancelDueAt())) {
            throw new BusinessException(ErrorCode.CANCEL_WINDOW_CLOSED,
                    "已过免费取消截止时间（" + reservation.getCancelDueAt() + "），无法取消");
        }

        // Terminal write (CANCEL event + settlement_result) happens inside the settlement service.
        settlementService.cancel(reservationId, now);
        idempotencyService.recordSuccess(userId, CMD_CANCEL, requestKey, hash,
                "RESERVATION", String.valueOf(reservationId));

        Reservation fresh = reservationMapper.selectById(reservationId);
        return new AttendanceResult(
                String.valueOf(reservationId),
                fresh.getStatus().name(),
                clock.toOffset(fresh.getCheckInAt()),
                null,
                clock.toOffset(fresh.getFinishedAt()),
                leaveCountOf(fresh),
                0L, 0, 0);
    }

    /**
     * Check-in with the six-digit on-site code. The caller (controller) has already
     * verified the code with BCrypt against the room row read outside the
     * transaction and passes that row's version and code hash in; inside the
     * transaction both must still match, otherwise the room changed (409 retry).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceResult checkIn(Long reservationId, Long userId, String code, String requestKey,
                                    Long roomVersionFromOutside, String checkinCodeHashFromOutside) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        String hash = Ids.requestHash(CMD_CHECK_IN + ":" + reservationId + ":" + code);
        idempotencyService.checkInTransaction(userId, CMD_CHECK_IN, requestKey, hash);

        Reservation reservation = loadOwnedForUpdate(reservationId, userId);

        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw BusinessException.invalidState(terminalHint(reservation, "订单当前状态不可签到"));
        }

        RuleConfig rule = ruleFor(reservation);

        // Deadline passed mid-flight: settle first (no-show), then reject.
        if (DeadlineRules.noShowDue(now, reservation.getCheckinDueAt(), reservation.getEndAt())) {
            settlementService.settleOne(reservationId, now, SETTLE_TRIGGER_COMMAND);
            throw new BusinessException(ErrorCode.CHECKIN_WINDOW_CLOSED,
                    "已过签到截止时间，订单已按失约处理");
        }
        if (!now.isBefore(reservation.getEndAt())) {
            settlementService.settleOne(reservationId, now, SETTLE_TRIGGER_COMMAND);
            throw new BusinessException(ErrorCode.CHECKIN_WINDOW_CLOSED, "已超过预约结束时间，无法签到");
        }

        LocalDateTime windowOpen = reservation.getStartAt().minusMinutes(rule.checkinEarlyMinutes());
        LocalDateTime windowClose = reservation.getStartAt().plusMinutes(rule.checkinLateMinutes());
        if (now.isBefore(windowOpen)) {
            throw new BusinessException(ErrorCode.CHECKIN_WINDOW_CLOSED,
                    "签到尚未开始，最早 " + windowOpen + " 可签到");
        }
        if (now.isAfter(windowClose)) {
            throw new BusinessException(ErrorCode.CHECKIN_WINDOW_CLOSED, "已过签到截止时间，无法签到");
        }

        // Room must still be the row verified outside and currently open.
        StudyRoom room = studyRoomMapper.selectById(reservation.getRoomId());
        if (room == null) {
            throw BusinessException.notFound("自习室不存在");
        }
        if (!Objects.equals(room.getVersion(), roomVersionFromOutside)
                || !Objects.equals(room.getCheckinCodeHash(), checkinCodeHashFromOutside)) {
            throw BusinessException.invalidState("自习室信息已变更，请刷新后重试");
        }
        if (room.getStatus() != RoomStatus.OPEN) {
            throw new BusinessException(ErrorCode.OUTSIDE_OPEN_HOURS, "自习室已关闭，暂不可签到");
        }
        if (now.toLocalTime().isBefore(room.getOpenTime())
                || !now.toLocalTime().isBefore(room.getCloseTime())) {
            throw new BusinessException(ErrorCode.OUTSIDE_OPEN_HOURS,
                    "当前不在自习室开放时间（" + room.getOpenTime() + "—" + room.getCloseTime() + "）内");
        }

        // Early check-in extends the effective right backwards to now: neither the
        // seat nor the student may be held by another effective order right now.
        if (now.isBefore(reservation.getStartAt())) {
            List<Reservation> seatBusy = reservationMapper.selectOverlappingForSeat(
                    reservation.getSeatId(), now, reservation.getEndAt());
            List<Reservation> userBusy = reservationMapper.selectOverlappingForUser(
                    userId, now, reservation.getEndAt());
            boolean seatTaken = seatBusy.stream().anyMatch(other -> !other.getId().equals(reservation.getId()));
            boolean userTaken = userBusy.stream().anyMatch(other -> !other.getId().equals(reservation.getId()));
            if (seatTaken || userTaken) {
                throw new BusinessException(ErrorCode.SEAT_CURRENTLY_OCCUPIED,
                        "前一使用权尚未结束，暂时无法提前签到");
            }
        }

        writeEvent(reservation.getId(), AttendanceEventType.CHECK_IN, now, userId, "checkin:" + requestKey);
        reservation.setStatus(ReservationStatus.IN_USE);
        reservation.setCheckInAt(now);
        reservation.setUpdatedAt(now);
        requireOneRow(reservationMapper.updateState(reservation));

        idempotencyService.recordSuccess(userId, CMD_CHECK_IN, requestKey, hash,
                "RESERVATION", String.valueOf(reservationId));

        return new AttendanceResult(String.valueOf(reservationId), ReservationStatus.IN_USE.name(),
                clock.toOffset(now), null, null, leaveCountOf(reservation), 0L, 0, 0);
    }

    /**
     * Temporary leave. At most leaveMaxCount successful leaves per order; the
     * counter only increments when the LEFT_TEMP transition actually commits.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceResult leave(Long reservationId, Long userId, String requestKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        String hash = Ids.requestHash(CMD_LEAVE + ":" + reservationId);
        idempotencyService.checkInTransaction(userId, CMD_LEAVE, requestKey, hash);

        Reservation reservation = loadOwnedForUpdate(reservationId, userId);

        if (reservation.getStatus() != ReservationStatus.IN_USE) {
            throw BusinessException.invalidState(terminalHint(reservation, "订单当前状态不可暂离"));
        }
        if (!now.isBefore(reservation.getEndAt())) {
            settlementService.settleOne(reservationId, now, SETTLE_TRIGGER_COMMAND);
            throw BusinessException.invalidState("已超过预约结束时间，无法暂离");
        }

        RuleConfig rule = ruleFor(reservation);
        int used = leaveCountOf(reservation);
        if (used >= rule.leaveMaxCount()) {
            throw new BusinessException(ErrorCode.LEAVE_LIMIT_REACHED,
                    "暂离次数已达上限（每单最多 " + rule.leaveMaxCount() + " 次）");
        }

        LocalDateTime leaveDue = DeadlineRules.leaveDueAt(now, rule);
        writeEvent(reservation.getId(), AttendanceEventType.LEAVE, now, userId, "leave:" + requestKey);
        reservation.setStatus(ReservationStatus.LEFT_TEMP);
        reservation.setLastLeaveAt(now);
        reservation.setLeaveDueAt(leaveDue);
        reservation.setLeaveCount(used + 1);
        reservation.setUpdatedAt(now);
        requireOneRow(reservationMapper.updateState(reservation));

        idempotencyService.recordSuccess(userId, CMD_LEAVE, requestKey, hash,
                "RESERVATION", String.valueOf(reservationId));

        return new AttendanceResult(String.valueOf(reservationId), ReservationStatus.LEFT_TEMP.name(),
                clock.toOffset(reservation.getCheckInAt()), clock.toOffset(leaveDue), null, used + 1, 0L, 0, 0);
    }

    /**
     * Return from a temporary leave. Allowed exactly at the leave deadline
     * (closed end) and strictly before the reservation end; a passed leave
     * deadline is settled as LEAVE_TIMEOUT and rejected.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceResult returnFromLeave(Long reservationId, Long userId, String requestKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        String hash = Ids.requestHash(CMD_RETURN + ":" + reservationId);
        idempotencyService.checkInTransaction(userId, CMD_RETURN, requestKey, hash);

        Reservation reservation = loadOwnedForUpdate(reservationId, userId);

        if (reservation.getStatus() != ReservationStatus.LEFT_TEMP) {
            throw BusinessException.invalidState(terminalHint(reservation, "订单当前不在暂离状态"));
        }
        LocalDateTime leaveDue = reservation.getLeaveDueAt();
        if (leaveDue != null && now.isAfter(leaveDue)) {
            settlementService.settleOne(reservationId, now, SETTLE_TRIGGER_COMMAND);
            throw BusinessException.invalidState("暂离已超时，订单已按违约处理，无法回座");
        }
        if (!now.isBefore(reservation.getEndAt())) {
            settlementService.settleOne(reservationId, now, SETTLE_TRIGGER_COMMAND);
            throw BusinessException.invalidState("已超过预约结束时间，无法回座");
        }

        writeEvent(reservation.getId(), AttendanceEventType.RETURN, now, userId, "return:" + requestKey);
        reservation.setStatus(ReservationStatus.IN_USE);
        reservation.setLeaveDueAt(null);
        reservation.setUpdatedAt(now);
        requireOneRow(reservationMapper.updateState(reservation));

        idempotencyService.recordSuccess(userId, CMD_RETURN, requestKey, hash,
                "RESERVATION", String.valueOf(reservationId));

        return new AttendanceResult(String.valueOf(reservationId), ReservationStatus.IN_USE.name(),
                clock.toOffset(reservation.getCheckInAt()), null, null, leaveCountOf(reservation), 0L, 0, 0);
    }

    /**
     * Student check-out. Before end it completes with a reward computed from the
     * actual in-seat seconds; at or after end the settlement degrades to AUTO_END
     * with zero reward. An overstayed leave is settled as a violation first —
     * check-out can never launder a penalty.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceResult checkOut(Long reservationId, Long userId, String requestKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        String hash = Ids.requestHash(CMD_CHECK_OUT + ":" + reservationId);
        idempotencyService.checkInTransaction(userId, CMD_CHECK_OUT, requestKey, hash);

        Reservation reservation = loadOwnedForUpdate(reservationId, userId);

        if (reservation.getStatus() != ReservationStatus.IN_USE
                && reservation.getStatus() != ReservationStatus.LEFT_TEMP) {
            throw BusinessException.invalidState(terminalHint(reservation, "订单当前状态不可签退"));
        }
        if (reservation.getStatus() == ReservationStatus.LEFT_TEMP
                && reservation.getLeaveDueAt() != null && now.isAfter(reservation.getLeaveDueAt())) {
            settlementService.settleOne(reservationId, now, SETTLE_TRIGGER_COMMAND);
            throw BusinessException.invalidState("暂离已超时，订单已按违约处理，无法签退");
        }

        SettlementOutcome outcome = settlementService.checkout(reservationId, now);
        idempotencyService.recordSuccess(userId, CMD_CHECK_OUT, requestKey, hash,
                "RESERVATION", String.valueOf(reservationId));

        Reservation fresh = reservationMapper.selectById(reservationId);
        return new AttendanceResult(
                String.valueOf(reservationId),
                fresh.getStatus().name(),
                clock.toOffset(fresh.getCheckInAt()),
                null,
                clock.toOffset(fresh.getFinishedAt()),
                leaveCountOf(fresh),
                outcome == null ? 0L : outcome.eligibleSeconds(),
                outcome == null ? 0 : outcome.rewardRequested(),
                outcome == null ? 0 : outcome.rewardActual());
    }

    // ---------- helpers ----------

    private Reservation loadOwnedForUpdate(Long reservationId, Long userId) {
        Reservation reservation = reservationMapper.selectByIdForUpdate(reservationId);
        if (reservation == null || !reservation.getUserId().equals(userId)) {
            throw BusinessException.notFound("预约不存在或无权访问");
        }
        return reservation;
    }

    private void writeEvent(Long reservationId, AttendanceEventType type, LocalDateTime at,
                            Long operatorId, String eventRequestKey) {
        AttendanceEvent event = new AttendanceEvent();
        event.setReservationId(reservationId);
        event.setEventType(type);
        event.setEventAt(at);
        event.setOperatorId(operatorId);
        event.setRequestKey(eventRequestKey);
        event.setCreatedAt(at);
        attendanceEventMapper.insert(event);
    }

    private static void requireOneRow(int affectedRows) {
        if (affectedRows != 1) {
            throw BusinessException.invalidState("订单状态已变更，请刷新后重试");
        }
    }

    private static int leaveCountOf(Reservation reservation) {
        return reservation.getLeaveCount() == null ? 0 : reservation.getLeaveCount();
    }

    /** Friendlier rejection text for terminal orders, keyed by termination reason. */
    private static String terminalHint(Reservation reservation, String fallback) {
        if (reservation.getTerminationReason() == null) {
            return fallback;
        }
        return switch (reservation.getTerminationReason()) {
            case NO_SHOW -> "已过签到截止时间，订单已按失约处理";
            case LEAVE_TIMEOUT -> "暂离已超时，订单已按违约处理";
            case STUDENT_CANCEL -> "订单已取消";
            case STUDENT_CHECKOUT -> "订单已签退完成";
            case AUTO_END -> "订单已到期自动结束";
            default -> fallback;
        };
    }

    /** The order's own rule snapshot governs its deadlines; fall back to the active rule. */
    private RuleConfig ruleFor(Reservation reservation) {
        String snapshot = reservation.getRuleSnapshot();
        if (snapshot != null && !snapshot.isBlank()) {
            try {
                return RuleConfig.fromJson(snapshot);
            } catch (RuntimeException ex) {
                // fall through to the active rule
            }
        }
        return ruleService.activeRule();
    }
}
