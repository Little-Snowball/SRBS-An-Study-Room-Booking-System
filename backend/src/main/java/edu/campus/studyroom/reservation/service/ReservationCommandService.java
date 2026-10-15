package edu.campus.studyroom.reservation.service;

import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.IdempotencyService;
import edu.campus.studyroom.common.Ids;
import edu.campus.studyroom.credit.domain.CreditAccount;
import edu.campus.studyroom.credit.mapper.CreditAccountMapper;
import edu.campus.studyroom.reservation.domain.DeadlineRules;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.domain.TimeGrid;
import edu.campus.studyroom.reservation.dto.CreateReservationRequest;
import edu.campus.studyroom.reservation.dto.ReservationDetail;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.resource.domain.RoomStatus;
import edu.campus.studyroom.resource.domain.Seat;
import edu.campus.studyroom.resource.domain.SeatStatus;
import edu.campus.studyroom.resource.domain.StudyRoom;
import edu.campus.studyroom.resource.mapper.SeatMapper;
import edu.campus.studyroom.resource.mapper.StudyRoomMapper;
import edu.campus.studyroom.rule.BusinessGuardLock;
import edu.campus.studyroom.rule.RuleService;
import edu.campus.studyroom.rule.domain.RuleConfig;
import edu.campus.studyroom.rule.domain.RuleVersion;
import edu.campus.studyroom.user.domain.AppUser;
import edu.campus.studyroom.user.domain.UserStatus;
import edu.campus.studyroom.user.mapper.AppUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reservation creation command. The controller has already run the pre-flight
 * settlement; inside the guarded transaction every deadline is re-checked against
 * the single trusted clock. Deadlock-free by construction: the guard row lock is
 * the first lock every core write takes, serializing all writers.
 */
@Service
public class ReservationCommandService {

    private final BusinessGuardLock businessGuardLock;
    private final BusinessClock clock;
    private final IdempotencyService idempotencyService;
    private final AppUserMapper appUserMapper;
    private final CreditAccountMapper creditAccountMapper;
    private final RuleService ruleService;
    private final StudyRoomMapper studyRoomMapper;
    private final SeatMapper seatMapper;
    private final ReservationMapper reservationMapper;
    private final ReservationQueryService reservationQueryService;

    public ReservationCommandService(BusinessGuardLock businessGuardLock, BusinessClock clock,
                                     IdempotencyService idempotencyService, AppUserMapper appUserMapper,
                                     CreditAccountMapper creditAccountMapper, RuleService ruleService,
                                     StudyRoomMapper studyRoomMapper, SeatMapper seatMapper,
                                     ReservationMapper reservationMapper,
                                     ReservationQueryService reservationQueryService) {
        this.businessGuardLock = businessGuardLock;
        this.clock = clock;
        this.idempotencyService = idempotencyService;
        this.appUserMapper = appUserMapper;
        this.creditAccountMapper = creditAccountMapper;
        this.ruleService = ruleService;
        this.studyRoomMapper = studyRoomMapper;
        this.seatMapper = seatMapper;
        this.reservationMapper = reservationMapper;
        this.reservationQueryService = reservationQueryService;
    }

    /**
     * Creates one RESERVED order. Rejects with the documented ErrorCode:
     * ACCOUNT_DISABLED / ACCOUNT_BLOCKED / CREDIT_INSUFFICIENT / INVALID_TIME_GRID /
     * OUTSIDE_OPEN_HOURS / SLOT_CONFLICT / USER_TIME_CONFLICT / NOT_FOUND.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationDetail createReservation(Long userId, CreateReservationRequest request, String requestKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();

        String requestHash = Ids.requestHash(request);
        idempotencyService.checkInTransaction(userId, "RESERVATION_CREATE", requestKey, requestHash);

        // --- user row (re-read under lock): ACTIVE and not blocked ---
        AppUser user = appUserMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号已禁用，无法预约");
        }
        if (Boolean.TRUE.equals(user.isBlocked())) {
            throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED, "账号已被封禁，无法预约");
        }

        RuleConfig rule = ruleService.activeRule();
        RuleVersion activeVersion = ruleService.activeVersion();

        // --- credit gate (threshold comes from the rule, never hard-coded) ---
        CreditAccount credit = creditAccountMapper.selectByUserIdForUpdate(userId);
        if (credit == null || credit.getScore() == null || credit.getScore() < rule.minCredit()) {
            throw new BusinessException(ErrorCode.CREDIT_INSUFFICIENT,
                    "信用分不足，预约至少需要 " + rule.minCredit() + " 分");
        }

        // --- time window: grid, duration, no cross-day, advance window ---
        Long seatId = parseSeatId(request.seatId());
        LocalDateTime startAt = clock.toLocal(request.startAt());
        LocalDateTime endAt = clock.toLocal(request.endAt());
        int slotMinutes = rule.slotMinutes();

        if (!TimeGrid.isOnGrid(startAt, slotMinutes) || !TimeGrid.isOnGrid(endAt, slotMinutes)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_GRID,
                    "开始与结束时间必须落在整 " + slotMinutes + " 分钟格点上");
        }
        long minutes = Duration.between(startAt, endAt).toMinutes();
        if (minutes < rule.minMinutes() || minutes > rule.maxMinutes() || minutes % slotMinutes != 0) {
            throw new BusinessException(ErrorCode.INVALID_TIME_GRID,
                    "预约时长须为 " + slotMinutes + " 分钟的倍数，且在 "
                            + rule.minMinutes() + "—" + rule.maxMinutes() + " 分钟之间");
        }
        if (!startAt.toLocalDate().equals(endAt.toLocalDate())) {
            throw new BusinessException(ErrorCode.INVALID_TIME_GRID, "预约不可跨天，请在同一天内选择时段");
        }
        LocalDate today = clock.today();
        LocalDate startDate = startAt.toLocalDate();
        if (startDate.isBefore(today) || startDate.isAfter(today.plusDays(rule.advanceDays()))) {
            throw new BusinessException(ErrorCode.INVALID_TIME_GRID,
                    "仅可预约 " + today + " 至 " + today.plusDays(rule.advanceDays()) + " 范围内的日期");
        }

        // --- seat and room: must exist, room OPEN, seat NORMAL ---
        Seat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            throw BusinessException.notFound("座位不存在");
        }
        StudyRoom room = studyRoomMapper.selectById(seat.getRoomId());
        if (room == null) {
            throw BusinessException.notFound("座位所属自习室不存在");
        }
        if (room.getStatus() != RoomStatus.OPEN) {
            throw new BusinessException(ErrorCode.OUTSIDE_OPEN_HOURS, "自习室已关闭，暂不可预约");
        }
        if (seat.getStatus() != SeatStatus.NORMAL) {
            throw new BusinessException(ErrorCode.OUTSIDE_OPEN_HOURS, "座位维护中，暂不可预约");
        }

        // --- same-day earliest start and full containment in open hours ---
        if (startDate.equals(today)) {
            LocalDateTime earliest = TimeGrid.earliestStart(now, room.getOpenTime(), slotMinutes);
            if (startAt.isBefore(earliest)) {
                throw new BusinessException(ErrorCode.INVALID_TIME_GRID,
                        "开始时间不能早于当前最早可约时段 " + earliest);
            }
        }
        LocalDateTime openAt = LocalDateTime.of(startDate, room.getOpenTime());
        LocalDateTime closeAt = LocalDateTime.of(startDate, room.getCloseTime());
        if (startAt.isBefore(openAt) || endAt.isAfter(closeAt)) {
            throw new BusinessException(ErrorCode.OUTSIDE_OPEN_HOURS,
                    "预约时段须完整处于自习室开放时间 " + room.getOpenTime() + "—" + room.getCloseTime() + " 内");
        }

        // --- seat and student must both be free for the whole half-open range ---
        List<Reservation> seatConflicts = reservationMapper.selectOverlappingForSeat(seatId, startAt, endAt);
        if (!seatConflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "该座位在所选时段已有预约，请更换时段或座位");
        }
        List<Reservation> userConflicts = reservationMapper.selectOverlappingForUser(userId, startAt, endAt);
        if (!userConflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.USER_TIME_CONFLICT, "您在所选时段已有其他预约，请勿重复预约");
        }

        // --- insert RESERVED order with the rule snapshot ---
        Reservation reservation = new Reservation();
        reservation.setOrderNo(Ids.newOrderNo(now));
        reservation.setUserId(userId);
        reservation.setRoomId(room.getId());
        reservation.setSeatId(seat.getId());
        reservation.setRoomNameSnapshot(room.getName());
        reservation.setSeatCodeSnapshot(seat.getCode());
        reservation.setStartAt(startAt);
        reservation.setEndAt(endAt);
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setRuleVersionId(activeVersion.getId());
        reservation.setRuleSnapshot(rule.toJson());
        reservation.setCheckinDueAt(DeadlineRules.checkinDue(startAt, rule, endAt));
        reservation.setCancelDueAt(DeadlineRules.cancelDue(startAt, rule));
        reservation.setLeaveCount(0);
        reservation.setVersion(0L);
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);
        reservationMapper.insert(reservation);

        idempotencyService.recordSuccess(userId, "RESERVATION_CREATE", requestKey, requestHash,
                "RESERVATION", String.valueOf(reservation.getId()));

        return reservationQueryService.toDetail(reservation, rule);
    }

    private static Long parseSeatId(String seatId) {
        try {
            return Long.parseLong(seatId.trim());
        } catch (NumberFormatException ex) {
            throw BusinessException.validation("座位 ID 不合法");
        }
    }
}
