package edu.campus.studyroom.resource.service;

import edu.campus.studyroom.audit.AuditService;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.notification.NotificationService;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.domain.TerminationReason;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.resource.ResourceSnapshots;
import edu.campus.studyroom.resource.domain.*;
import edu.campus.studyroom.resource.dto.*;
import edu.campus.studyroom.resource.mapper.ResourceStatusEventMapper;
import edu.campus.studyroom.resource.mapper.RetainedServiceMapper;
import edu.campus.studyroom.resource.mapper.SeatMapper;
import edu.campus.studyroom.resource.mapper.StudyRoomMapper;
import edu.campus.studyroom.rule.BusinessGuardLock;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import edu.campus.studyroom.settlement.SettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seat maintenance: batch generation, label editing, maintenance status.
 * Batch generation is strictly all-or-nothing: any conflicting position or code
 * fails the whole batch (409) without inserting anything.
 */
@Service
public class SeatService {

    private static final int MAX_TOTAL_SEATS = 1000;

    private final SeatMapper seatMapper;
    private final StudyRoomMapper roomMapper;
    private final ReservationMapper reservationMapper;
    private final ResourceStatusEventMapper eventMapper;
    private final RetainedServiceMapper retainedServiceMapper;
    private final BusinessGuardLock guardLock;
    private final BusinessClock clock;
    private final SettlementService settlementService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public SeatService(SeatMapper seatMapper, StudyRoomMapper roomMapper, ReservationMapper reservationMapper,
                       ResourceStatusEventMapper eventMapper, RetainedServiceMapper retainedServiceMapper,
                       BusinessGuardLock guardLock, BusinessClock clock, SettlementService settlementService,
                       NotificationService notificationService, AuditService auditService) {
        this.seatMapper = seatMapper;
        this.roomMapper = roomMapper;
        this.reservationMapper = reservationMapper;
        this.eventMapper = eventMapper;
        this.retainedServiceMapper = retainedServiceMapper;
        this.guardLock = guardLock;
        this.clock = clock;
        this.settlementService = settlementService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // ---------- batch generate ----------

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int batchGenerate(CurrentUserPrincipal admin, Long roomId, BatchSeatRequest request) {
        guardLock.lock();
        LocalDateTime now = clock.nowLocal();
        StudyRoom room = roomMapper.selectByIdForUpdate(roomId);
        if (room == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "自习室不存在");
        }
        if (request.startRow() > request.endRow() || request.startCol() > request.endCol()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "起始行列不得大于结束行列");
        }
        if (request.endRow() > room.getRowsCount() || request.endCol() > room.getColsCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "坐标越界：房间布局为 " + room.getRowsCount() + " 行 x " + room.getColsCount() + " 列");
        }
        long totalAfter = seatMapper.countAll()
                + (long) (request.endRow() - request.startRow() + 1) * (request.endCol() - request.startCol() + 1);
        if (totalAfter > MAX_TOTAL_SEATS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "新增后实际座位总数不得超过 " + MAX_TOTAL_SEATS);
        }

        // 先计算完整坐标/编号并校验重名，任何冲突整批失败
        List<Seat> seats = new ArrayList<>();
        for (int row = request.startRow(); row <= request.endRow(); row++) {
            for (int col = request.startCol(); col <= request.endCol(); col++) {
                if (seatMapper.selectByRoomRowCol(roomId, row, col) != null) {
                    throw new BusinessException(ErrorCode.SLOT_CONFLICT,
                            "位置 R" + row + "-C" + col + " 已存在座位，整批生成失败");
                }
                String code = seatCode(request.codePrefix(), row, col);
                if (seatMapper.selectByRoomAndCode(roomId, code) != null) {
                    throw new BusinessException(ErrorCode.SLOT_CONFLICT,
                            "编号 " + code + " 已存在，整批生成失败");
                }
                Seat seat = new Seat();
                seat.setRoomId(roomId);
                seat.setCode(code);
                seat.setRowNo(row);
                seat.setColNo(col);
                seat.setHasPower(Boolean.TRUE.equals(request.hasPower()));
                seat.setNearWindow(Boolean.TRUE.equals(request.nearWindow()));
                seat.setQuietZone(Boolean.TRUE.equals(request.quietZone()));
                seat.setStatus(SeatStatus.NORMAL);
                seat.setVersion(0L);
                seat.setCreatedAt(now);
                seat.setUpdatedAt(now);
                seats.add(seat);
            }
        }
        seatMapper.insertBatch(seats);
        auditService.record(admin.userId(), "SEAT_BATCH_CREATE", "STUDY_ROOM", String.valueOf(roomId),
                "{\"count\":" + seats.size() + ",\"prefix\":\"" + request.codePrefix() + "\"}");
        return seats.size();
    }

    /** prefix-R两位行号-C两位列号；超过 99 保持完整数字。 */
    static String seatCode(String prefix, int row, int col) {
        return prefix + "-R" + pad(row) + "-C" + pad(col);
    }

    private static String pad(int value) {
        return value < 100 ? String.format("%02d", value) : String.valueOf(value);
    }

    // ---------- labels ----------

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SeatItem updateLabels(CurrentUserPrincipal admin, Long seatId, UpdateSeatRequest request) {
        guardLock.lock();
        LocalDateTime now = clock.nowLocal();
        Seat seat = seatMapper.selectByIdForUpdate(seatId);
        if (seat == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "座位不存在");
        }
        if (!seat.getVersion().equals(request.expectedVersion())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "座位已被其他管理员更新，请刷新后重试");
        }
        int rows = seatMapper.updateLabels(seatId,
                request.hasPower() == null ? seat.isHasPower() : request.hasPower(),
                request.nearWindow() == null ? seat.isNearWindow() : request.nearWindow(),
                request.quietZone() == null ? seat.isQuietZone() : request.quietZone(),
                request.expectedVersion(), now);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "座位状态已变化，请刷新后重试");
        }
        auditService.record(admin.userId(), "SEAT_UPDATE", "SEAT", String.valueOf(seatId), "{}");
        return toItem(seatMapper.selectById(seatId));
    }

    // ---------- status ----------

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SeatStatusOutcome updateStatus(CurrentUserPrincipal admin, Long seatId, SeatStatusRequest request) {
        guardLock.lock();
        LocalDateTime now = clock.nowLocal();
        Seat seat = seatMapper.selectByIdForUpdate(seatId);
        if (seat == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "座位不存在");
        }
        if (!seat.getVersion().equals(request.expectedVersion())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "座位已被其他管理员更新，请刷新后重试");
        }
        SeatStatus target;
        try {
            target = SeatStatus.valueOf(request.status());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status 仅支持 NORMAL/MAINTENANCE");
        }
        if (target == seat.getStatus()) {
            return new SeatStatusOutcome(toItem(seat), 0, 0, 0);
        }

        int cancelled = 0;
        int kept = 0;
        int ended = 0;
        if (target == SeatStatus.MAINTENANCE) {
            if (request.orderPolicy() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "停用座位必须指定订单处理策略");
            }
            OrderPolicy policy;
            try {
                policy = OrderPolicy.valueOf(request.orderPolicy());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "orderPolicy 仅支持 KEEP_ACTIVE/FORCE_END");
            }
            List<Reservation> effective = reservationMapper.selectEffectiveBySeatId(seatId);
            for (Reservation order : effective) {
                if (order.getStatus() == ReservationStatus.RESERVED) {
                    settlementService.adminTerminate(order.getId(), now, TerminationReason.SEAT_MAINTENANCE);
                    cancelled++;
                    notifyOrder(order, "SEAT_MAINTENANCE", "座位维修停用", "座位已设为维修停用，您的预约已被无责取消");
                } else if (policy == OrderPolicy.FORCE_END) {
                    settlementService.adminTerminate(order.getId(), now, TerminationReason.SEAT_MAINTENANCE);
                    ended++;
                    notifyOrder(order, "SEAT_MAINTENANCE", "座位维修停用", "座位已设为维修停用，您的进行中订单已被无责终止");
                } else {
                    writeRetained(order, now, null);
                    kept++;
                    notifyOrder(order, "SEAT_MAINTENANCE", "座位维修停用", "座位已设为维修停用，您的进行中订单保留至预约结束");
                }
            }
        }

        int rows = seatMapper.updateStatus(seatId, request.expectedVersion(), target, now);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "座位状态已变化，请刷新后重试");
        }
        recordSeatEvent(seat, target, request.orderPolicy(), now, admin.userId());
        auditService.record(admin.userId(), "SEAT_STATUS", "SEAT", String.valueOf(seatId),
                "{\"status\":\"" + target + "\",\"cancelled\":" + cancelled + ",\"kept\":" + kept
                        + ",\"ended\":" + ended + "}");
        return new SeatStatusOutcome(toItem(seatMapper.selectById(seatId)), cancelled, kept, ended);
    }

    // ---------- impact preview ----------

    public SeatImpact impact(Long seatId, String newStatus) {
        Seat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "座位不存在");
        }
        List<Reservation> effective = reservationMapper.selectEffectiveBySeatId(seatId);
        long reserved = effective.stream().filter(o -> o.getStatus() == ReservationStatus.RESERVED).count();
        long inUse = effective.stream().filter(o -> o.getStatus() == ReservationStatus.IN_USE).count();
        long leftTemp = effective.stream().filter(o -> o.getStatus() == ReservationStatus.LEFT_TEMP).count();
        return new SeatImpact(String.valueOf(seatId), seat.getCode(), seat.getStatus(),
                (int) reserved, (int) inUse, (int) leftTemp, (int) (reserved + inUse + leftTemp));
    }

    // ---------- layout ----------

    public List<SeatItem> layout(Long roomId) {
        return seatMapper.selectByRoomId(roomId).stream().map(SeatService::toItem).toList();
    }

    // ---------- helpers ----------

    private void writeRetained(Reservation order, LocalDateTime now, Long causeEventId) {
        RetainedService retained = new RetainedService();
        retained.setReservationId(order.getId());
        retained.setSeatId(order.getSeatId());
        retained.setFromAt(now);
        retained.setToAt(order.getEndAt());
        retained.setCauseEventId(causeEventId);
        retained.setCreatedAt(now);
        retainedServiceMapper.insert(retained);
    }

    private void notifyOrder(Reservation order, String type, String title, String content) {
        notificationService.publish(order.getUserId(), type.toLowerCase() + ":order:" + order.getId(), type,
                title, content, order.getId(), null, clock.nowLocal());
    }

    private void recordSeatEvent(Seat seat, SeatStatus target, String orderPolicy, LocalDateTime now,
                                 Long operatorId) {
        ResourceStatusEvent event = new ResourceStatusEvent();
        event.setResourceType(ResourceType.SEAT);
        event.setEventType(ResourceEventType.STATUS_CHANGED);
        event.setRoomId(seat.getRoomId());
        event.setSeatId(seat.getId());
        event.setAfterSnapshot(ResourceSnapshots.seat(target.name()));
        if (orderPolicy != null) {
            try {
                event.setOrderPolicy(OrderPolicy.valueOf(orderPolicy));
            } catch (IllegalArgumentException ignored) {
                // 策略缺省时不留痕，状态快照仍完整
            }
        }
        event.setEffectiveAt(now);
        event.setOperatorId(operatorId);
        event.setCreatedAt(now);
        eventMapper.insert(event);
    }

    public static SeatItem toItem(Seat seat) {
        return new SeatItem(String.valueOf(seat.getId()), String.valueOf(seat.getRoomId()), seat.getCode(),
                seat.getRowNo(), seat.getColNo(), seat.isHasPower(), seat.isNearWindow(), seat.isQuietZone(),
                seat.getStatus(), seat.getVersion() == null ? 0L : seat.getVersion());
    }

    /** 座位状态变更执行结果。 */
    public record SeatStatusOutcome(SeatItem seat, int affectedCancelled, int affectedKept,
                                    int affectedEnded) {
    }
}
