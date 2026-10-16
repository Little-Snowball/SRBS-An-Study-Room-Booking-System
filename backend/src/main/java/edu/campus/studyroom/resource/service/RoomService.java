package edu.campus.studyroom.resource.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campus.studyroom.audit.AuditService;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.IdempotencyService;
import edu.campus.studyroom.common.Ids;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.notification.NotificationService;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.domain.TerminationReason;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.resource.domain.OrderPolicy;
import edu.campus.studyroom.resource.domain.ResourceEventType;
import edu.campus.studyroom.resource.domain.ResourceStatusEvent;
import edu.campus.studyroom.resource.domain.ResourceType;
import edu.campus.studyroom.resource.domain.RetainedService;
import edu.campus.studyroom.resource.domain.RoomStatus;
import edu.campus.studyroom.resource.domain.Seat;
import edu.campus.studyroom.resource.domain.SeatStatus;
import edu.campus.studyroom.resource.domain.StudyRoom;
import edu.campus.studyroom.resource.dto.CheckinCodeRequest;
import edu.campus.studyroom.resource.dto.CreateRoomRequest;
import edu.campus.studyroom.resource.dto.RoomDetail;
import edu.campus.studyroom.resource.dto.RoomImpact;
import edu.campus.studyroom.resource.dto.RoomListItem;
import edu.campus.studyroom.resource.dto.RoomStatusRequest;
import edu.campus.studyroom.resource.dto.RoomStatusResult;
import edu.campus.studyroom.resource.dto.SeatItem;
import edu.campus.studyroom.resource.dto.UpdateRoomRequest;
import edu.campus.studyroom.resource.mapper.ResourceStatusEventMapper;
import edu.campus.studyroom.resource.mapper.RetainedServiceMapper;
import edu.campus.studyroom.resource.mapper.SeatMapper;
import edu.campus.studyroom.resource.mapper.StudyRoomMapper;
import edu.campus.studyroom.rule.BusinessGuardLock;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import edu.campus.studyroom.security.PasswordPolicy;
import edu.campus.studyroom.settlement.SettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Room maintenance commands (create / edit basics / open-close / rotate check-in
 * code) plus admin-side reads. Every management write runs READ COMMITTED with
 * the business guard lock taken first, then the room row itself; a lost version
 * update fails with affectedRows=0 instead of silently overwriting.
 *
 * <p>Order cascades are policy-driven and always recorded: RESERVED orders are
 * cancelled without fault; IN_USE/LEFT_TEMP orders are either force-ended
 * (zero reward/penalty) or kept active with a retained_service permission
 * interval. Notifications are written in the same transaction.
 */
@Service
public class RoomService {

    /** Above this many effective orders a single room change refuses with BUSY_RETRY. */
    static final long MAX_AFFECTED_ORDERS = 1000L;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9-]{1,32}$");
    private static final Pattern CHECKIN_CODE_PATTERN = Pattern.compile("^[0-9]{6}$");
    private static final DateTimeFormatter AUDIT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BusinessGuardLock businessGuardLock;
    private final BusinessClock clock;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final PasswordPolicy passwordPolicy;
    private final StudyRoomMapper studyRoomMapper;
    private final SeatMapper seatMapper;
    private final ReservationMapper reservationMapper;
    private final ResourceStatusEventMapper resourceStatusEventMapper;
    private final RetainedServiceMapper retainedServiceMapper;
    private final SettlementService settlementService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public RoomService(BusinessGuardLock businessGuardLock, BusinessClock clock,
                       IdempotencyService idempotencyService, ObjectMapper objectMapper,
                       PasswordPolicy passwordPolicy, StudyRoomMapper studyRoomMapper, SeatMapper seatMapper,
                       ReservationMapper reservationMapper, ResourceStatusEventMapper resourceStatusEventMapper,
                       RetainedServiceMapper retainedServiceMapper, SettlementService settlementService,
                       NotificationService notificationService, AuditService auditService) {
        this.businessGuardLock = businessGuardLock;
        this.clock = clock;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.passwordPolicy = passwordPolicy;
        this.studyRoomMapper = studyRoomMapper;
        this.seatMapper = seatMapper;
        this.reservationMapper = reservationMapper;
        this.resourceStatusEventMapper = resourceStatusEventMapper;
        this.retainedServiceMapper = retainedServiceMapper;
        this.settlementService = settlementService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // ---------------------------------------------------------------- commands

    /**
     * Creates a room. The check-in code is stored only as a BCrypt hash.
     * A CREATED resource event carries the after-snapshot for history rebuild.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomDetail create(CurrentUserPrincipal admin, CreateRoomRequest request, String requestKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        checkIdempotent(admin.userId(), "ROOM_CREATE", requestKey, request);

        String code = request.code().trim();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw BusinessException.validation("房间编码须为大写字母、数字或短横线，长度 1—32");
        }
        if (!CHECKIN_CODE_PATTERN.matcher(request.checkinCode().trim()).matches()) {
            throw BusinessException.validation("现场码须为六位数字");
        }
        validateHours(request.openTime(), request.closeTime());
        validateGrid(request.rows(), request.cols());
        if (studyRoomMapper.selectByCode(code) != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "房间编码已存在，请更换编码");
        }

        StudyRoom room = new StudyRoom();
        room.setCode(code);
        room.setName(request.name().trim());
        room.setCampus(request.campus().trim());
        room.setBuilding(request.building().trim());
        room.setOpenTime(request.openTime());
        room.setCloseTime(request.closeTime());
        room.setRowsCount(request.rows());
        room.setColsCount(request.cols());
        room.setCheckinCodeHash(passwordPolicy.hash(request.checkinCode().trim()));
        room.setStatus(RoomStatus.OPEN);
        room.setVersion(0L);
        room.setCreatedAt(now);
        room.setUpdatedAt(now);
        studyRoomMapper.insert(room);

        Long eventId = insertRoomEvent(room, ResourceEventType.CREATED, room.getStatus(),
                room.getOpenTime(), room.getCloseTime(), room.getRowsCount(), room.getColsCount(),
                null, now, admin.userId());

        recordIdempotent(admin.userId(), "ROOM_CREATE", requestKey, request, "ROOM", String.valueOf(room.getId()));
        auditService.record(admin, "ROOM_CREATE", "ROOM", String.valueOf(room.getId()),
                "{\"action\":\"创建自习室\",\"roomId\":" + room.getId()
                        + ",\"code\":\"" + room.getCode() + "\",\"name\":\"" + room.getName()
                        + "\",\"eventId\":" + eventId + "}");

        return toDetail(room, 0L, 0L);
    }

    /**
     * Updates editable basics. Shrinking rows/cols below any existing seat
     * coordinate (including maintenance seats) is rejected. Shrinking the open
     * hours requires an explicit order policy and cascades to effective orders
     * that no longer fit inside the new hours.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomDetail updateBasics(CurrentUserPrincipal admin, Long roomId, UpdateRoomRequest request,
                                   String requestKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        checkIdempotent(admin.userId(), "ROOM_UPDATE", requestKey, request);

        StudyRoom room = studyRoomMapper.selectByIdForUpdate(roomId);
        if (room == null) {
            throw BusinessException.notFound("自习室不存在");
        }
        validateHours(request.openTime(), request.closeTime());
        validateGrid(request.rows(), request.cols());

        Integer maxRow = seatMapper.selectMaxRowNo(roomId);
        Integer maxCol = seatMapper.selectMaxColNo(roomId);
        if (maxRow != null && request.rows() < maxRow) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "行数不能缩小到 " + request.rows() + "：已有座位位于第 " + maxRow + " 行");
        }
        if (maxCol != null && request.cols() < maxCol) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "列数不能缩小到 " + request.cols() + "：已有座位位于第 " + maxCol + " 列");
        }

        boolean hoursChanged = !request.openTime().equals(room.getOpenTime())
                || !request.closeTime().equals(room.getCloseTime());
        boolean hoursShrunk = request.closeTime().isBefore(room.getCloseTime())
                || request.openTime().isAfter(room.getOpenTime());
        String newCode = request.code().trim();
        if (!newCode.equals(room.getCode()) && studyRoomMapper.selectByCode(newCode) != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "房间编码已存在，请更换编码");
        }
        OrderPolicy policy = null;
        List<Reservation> affected = List.of();
        if (hoursShrunk) {
            if (request.orderPolicy() == null) {
                throw BusinessException.validation("缩短开放时间必须指定 orderPolicy（KEEP_ACTIVE/FORCE_END）");
            }
            policy = request.orderPolicy();
            List<Reservation> effective = effectiveOrdersOfRoom(roomId);
            refuseWhenTooMany(effective.size());
            affected = filterOutsideHours(effective, request.openTime(), request.closeTime());
        }

        int updated = studyRoomMapper.updateBasics(roomId, newCode, request.name().trim(), request.campus().trim(),
                request.building().trim(), request.openTime(), request.closeTime(),
                request.rows(), request.cols(), request.expectedVersion(), now);
        requireOneRow(updated, "自习室基础信息更新");

        Long causeEventId = null;
        if (hoursChanged) {
            causeEventId = insertRoomEvent(room, ResourceEventType.HOURS_CHANGED, room.getStatus(),
                    request.openTime(), request.closeTime(), request.rows(), request.cols(),
                    policy, now, admin.userId());
        }

        PolicyCounters counters = new PolicyCounters(0, 0, 0);
        if (hoursShrunk) {
            counters = applyOrderPolicy(affected, TerminationReason.HOURS_CHANGED, policy, causeEventId,
                    "room:" + roomId + ":hours:" + causeEventId,
                    "自习室《" + room.getName() + "》开放时间已调整为 "
                            + request.openTime() + "—" + request.closeTime(), now);
        }

        recordIdempotent(admin.userId(), "ROOM_UPDATE", requestKey, request, "ROOM", String.valueOf(roomId));
        auditService.record(admin, "ROOM_UPDATE", "ROOM", String.valueOf(roomId),
                "{\"roomId\":" + roomId + ",\"code\":\"" + newCode
                        + "\",\"codeChanged\":" + !newCode.equals(room.getCode())
                        + ",\"hoursChanged\":" + hoursChanged
                        + ",\"orderPolicy\":" + (policy == null ? "null" : "\"" + policy.name() + "\"")
                        + ",\"cancelled\":" + counters.cancelled() + ",\"kept\":" + counters.kept()
                        + ",\"ended\":" + counters.ended() + "}");

        StudyRoom fresh = studyRoomMapper.selectById(roomId);
        return toDetail(fresh, seatMapper.countByRoomId(roomId),
                seatMapper.countByRoomIdAndStatus(roomId, SeatStatus.NORMAL));
    }

    /**
     * Opens or closes a room. Closing cascades to every effective order of the
     * room: RESERVED orders are cancelled without fault; IN_USE/LEFT_TEMP orders
     * follow the mandatory order policy (keep active with a retained-service
     * permission interval, or force-end with zero reward/penalty).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomStatusResult updateStatus(CurrentUserPrincipal admin, Long roomId, RoomStatusRequest request,
                                         String requestKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        checkIdempotent(admin.userId(), "ROOM_STATUS", requestKey, request);

        StudyRoom room = studyRoomMapper.selectByIdForUpdate(roomId);
        if (room == null) {
            throw BusinessException.notFound("自习室不存在");
        }
        if (request.orderPolicy() == null) {
            throw BusinessException.validation("必须指定 orderPolicy（KEEP_ACTIVE/FORCE_END）");
        }
        if (room.getStatus() == request.status()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "自习室状态未变化，无需更新");
        }

        List<Reservation> effective = request.status() == RoomStatus.CLOSED
                ? effectiveOrdersOfRoom(roomId) : List.of();
        refuseWhenTooMany(effective.size());

        int updated = studyRoomMapper.updateStatus(roomId, request.expectedVersion(), request.status(), now);
        requireOneRow(updated, "自习室状态更新");

        Long causeEventId = insertRoomEvent(room, ResourceEventType.STATUS_CHANGED, request.status(),
                room.getOpenTime(), room.getCloseTime(), room.getRowsCount(), room.getColsCount(),
                request.orderPolicy(), now, admin.userId());

        PolicyCounters counters = new PolicyCounters(0, 0, 0);
        if (request.status() == RoomStatus.CLOSED) {
            counters = applyOrderPolicy(effective, TerminationReason.ROOM_CLOSED, request.orderPolicy(),
                    causeEventId, "room:" + roomId + ":closed:" + causeEventId,
                    "自习室《" + room.getName() + "》已闭馆", now);
        }

        recordIdempotent(admin.userId(), "ROOM_STATUS", requestKey, request, "ROOM", String.valueOf(roomId));
        auditService.record(admin, "ROOM_STATUS", "ROOM", String.valueOf(roomId),
                "{\"roomId\":" + roomId + ",\"status\":\"" + request.status().name()
                        + "\",\"orderPolicy\":\"" + request.orderPolicy().name()
                        + "\",\"cancelled\":" + counters.cancelled() + ",\"kept\":" + counters.kept()
                        + ",\"ended\":" + counters.ended() + "}");

        return new RoomStatusResult(String.valueOf(roomId), request.status(),
                counters.cancelled(), counters.kept(), counters.ended());
    }

    /**
     * Rotates the room check-in code. The old code stops working immediately;
     * neither the code nor its hash ever leaves the service layer.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomDetail updateCheckinCode(CurrentUserPrincipal admin, Long roomId, CheckinCodeRequest request,
                                        String requestKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        checkIdempotent(admin.userId(), "ROOM_CHECKIN_CODE", requestKey, request);

        StudyRoom room = studyRoomMapper.selectByIdForUpdate(roomId);
        if (room == null) {
            throw BusinessException.notFound("自习室不存在");
        }
        if (!CHECKIN_CODE_PATTERN.matcher(request.newCode().trim()).matches()) {
            throw BusinessException.validation("现场码须为六位数字");
        }

        int updated = studyRoomMapper.updateCheckinCode(roomId, request.expectedVersion(),
                passwordPolicy.hash(request.newCode().trim()), now);
        requireOneRow(updated, "现场码更新");

        recordIdempotent(admin.userId(), "ROOM_CHECKIN_CODE", requestKey, request, "ROOM", String.valueOf(roomId));
        auditService.record(admin, "ROOM_CHECKIN_CODE", "ROOM", String.valueOf(roomId),
                "{\"roomId\":" + roomId + ",\"rotated\":true}");

        StudyRoom fresh = studyRoomMapper.selectById(roomId);
        return toDetail(fresh, seatMapper.countByRoomId(roomId),
                seatMapper.countByRoomIdAndStatus(roomId, SeatStatus.NORMAL));
    }

    /** Convenience overload used by the admin controller (no idempotency key). */
    public RoomDetail create(CurrentUserPrincipal admin, CreateRoomRequest request) {
        return create(admin, request, null);
    }

    /** Convenience overload used by the admin controller (no idempotency key). */
    public RoomDetail updateBasics(CurrentUserPrincipal admin, Long roomId, UpdateRoomRequest request) {
        return updateBasics(admin, roomId, request, null);
    }

    /** Convenience overload used by the admin controller (no idempotency key). */
    public RoomStatusResult updateStatus(CurrentUserPrincipal admin, Long roomId, RoomStatusRequest request) {
        return updateStatus(admin, roomId, request, null);
    }

    /** Convenience overload used by the admin controller (no idempotency key). */
    public RoomDetail updateCheckinCode(CurrentUserPrincipal admin, Long roomId, CheckinCodeRequest request) {
        return updateCheckinCode(admin, roomId, request, null);
    }

    // ------------------------------------------------------------------- reads

    /** Admin room paging for the admin controller: list-view shape with seat counts. */
    @Transactional(readOnly = true)
    public PageResult<RoomListItem> pageRooms(PageQuery page) {
        PageResult<RoomDetail> details = listForAdmin(null, null, null, page);
        java.util.List<RoomListItem> items = new java.util.ArrayList<>(details.items().size());
        for (RoomDetail detail : details.items()) {
            items.add(new RoomListItem(detail.id(), detail.code(), detail.name(), detail.campus(),
                    detail.building(), detail.openTime(), detail.closeTime(),
                    detail.rows(), detail.cols(), detail.status(), (int) detail.normalSeats()));
        }
        return PageResult.of(items, details.total(), details.page(), details.size());
    }

    /** Admin room list (plain CRUD view; seat availability lives on /api/v1/rooms). */
    @Transactional(readOnly = true)
    public PageResult<RoomDetail> listForAdmin(String campus, String building, RoomStatus status,
                                               PageQuery page) {
        List<StudyRoom> rooms = studyRoomMapper.selectPage(escapeLike(campus), escapeLike(building), status,
                page.offset(), page.size());
        long total = studyRoomMapper.countPage(escapeLike(campus), escapeLike(building), status);
        List<RoomDetail> items = new ArrayList<>(rooms.size());
        for (StudyRoom room : rooms) {
            items.add(toDetail(room, seatMapper.countByRoomId(room.getId()),
                    seatMapper.countByRoomIdAndStatus(room.getId(), SeatStatus.NORMAL)));
        }
        return PageResult.of(items, total, page.page(), page.size());
    }

    /** Room detail for any authenticated user; never exposes the check-in code hash. */
    @Transactional(readOnly = true)
    public RoomDetail getDetail(Long roomId) {
        StudyRoom room = studyRoomMapper.selectById(roomId);
        if (room == null) {
            throw BusinessException.notFound("自习室不存在");
        }
        return toDetail(room, seatMapper.countByRoomId(roomId),
                seatMapper.countByRoomIdAndStatus(roomId, SeatStatus.NORMAL));
    }

    /** Admin seat layout of one room (all seats including maintenance ones). */
    @Transactional(readOnly = true)
    public List<SeatItem> listSeats(Long roomId) {
        if (studyRoomMapper.selectById(roomId) == null) {
            throw BusinessException.notFound("自习室不存在");
        }
        List<SeatItem> items = new ArrayList<>();
        for (Seat seat : seatMapper.selectByRoomId(roomId)) {
            items.add(toSeatItem(seat));
        }
        return items;
    }

    /**
     * Impact preview for a planned room change. Estimates how many effective
     * orders a close or an hours change would touch, split by status. The
     * preview is an estimate only; the executing command re-reads under the guard.
     */
    @Transactional(readOnly = true)
    public RoomImpact impactPreview(Long roomId, RoomStatus newStatus, LocalTime newOpenTime,
                                    LocalTime newCloseTime) {
        StudyRoom room = studyRoomMapper.selectById(roomId);
        if (room == null) {
            throw BusinessException.notFound("自习室不存在");
        }
        if (newStatus == null && newOpenTime == null && newCloseTime == null) {
            throw BusinessException.validation("请至少提供 newStatus 或新的开放时间（newOpenTime/newCloseTime）");
        }
        if (newOpenTime != null && newCloseTime != null) {
            validateHours(newOpenTime, newCloseTime);
        }
        boolean closing = newStatus == RoomStatus.CLOSED;
        List<Reservation> affected = new ArrayList<>();
        for (Reservation order : effectiveOrdersOfRoom(roomId)) {
            boolean hit = closing;
            if (!hit && newOpenTime != null && newCloseTime != null) {
                hit = !fullyWithinHours(order, newOpenTime, newCloseTime);
            }
            if (hit) {
                affected.add(order);
            }
        }
        return new RoomImpact(String.valueOf(roomId), room.getStatus(), newStatus, newOpenTime, newCloseTime,
                (int) countByStatus(affected, ReservationStatus.RESERVED),
                (int) countByStatus(affected, ReservationStatus.IN_USE),
                (int) countByStatus(affected, ReservationStatus.LEFT_TEMP),
                affected.size(),
                "预览仅为预估，实际变更以执行时为准");
    }

    /** Impact preview overload for the admin controller: parse optional newStatus/newHours. */
    @Transactional(readOnly = true)
    public RoomImpact impact(Long roomId, String newStatus) {
        RoomStatus status = null;
        if (newStatus != null && !newStatus.isBlank()) {
            try {
                status = RoomStatus.valueOf(newStatus);
            } catch (IllegalArgumentException ex) {
                throw BusinessException.validation("newStatus 仅支持 OPEN/CLOSED");
            }
        }
        return impactPreview(roomId, status, null, null);
    }

    // ---------------------------------------------------------------- internals

    private PolicyCounters applyOrderPolicy(List<Reservation> orders, TerminationReason reason,
                                            OrderPolicy policy, Long causeEventId, String eventKeyPrefix,
                                            String changeDesc, LocalDateTime now) {
        int cancelled = 0;
        int kept = 0;
        int ended = 0;
        for (Reservation order : orders) {
            if (order.getStatus() == ReservationStatus.RESERVED) {
                // 未使用订单无责取消；策略只决定进行中订单的去向
                settlementService.adminTerminate(order.getId(), now, reason);
                cancelled++;
                notificationService.publish(order.getUserId(), eventKeyPrefix + ":order:" + order.getId(),
                        "RESOURCE_CANCEL", "预约已取消",
                        "由于" + changeDesc + "，您的预约（" + order.getOrderNo() + "，"
                                + order.getStartAt().format(AUDIT_TIME) + "—"
                                + order.getEndAt().format(AUDIT_TIME) + "）已无责取消，不影响信用分。",
                        order.getId(), null, now);
            } else if (policy == OrderPolicy.FORCE_END) {
                settlementService.adminTerminate(order.getId(), now, reason);
                ended++;
                notificationService.publish(order.getUserId(), eventKeyPrefix + ":order:" + order.getId(),
                        "RESOURCE_END", "预约已提前结束",
                        "由于" + changeDesc + "，您的进行中预约（" + order.getOrderNo()
                                + "）已提前结束，零奖励零扣分。",
                        order.getId(), null, now);
            } else {
                // KEEP_ACTIVE：保留进行中状态，写入保留使用权许可区间 [now, end_at)
                if (now.isBefore(order.getEndAt())) {
                    RetainedService retained = new RetainedService();
                    retained.setReservationId(order.getId());
                    retained.setSeatId(order.getSeatId());
                    retained.setFromAt(now);
                    retained.setToAt(order.getEndAt());
                    retained.setCauseEventId(causeEventId);
                    retained.setCreatedAt(now);
                    retainedServiceMapper.insert(retained);
                }
                kept++;
                notificationService.publish(order.getUserId(), eventKeyPrefix + ":order:" + order.getId(),
                        "RESOURCE_KEEP", "预约可继续使用",
                        "由于" + changeDesc + "，您的进行中预约（" + order.getOrderNo()
                                + "）可继续使用至原结束时间 " + order.getEndAt().format(AUDIT_TIME) + "。",
                        order.getId(), null, now);
            }
        }
        return new PolicyCounters(cancelled, kept, ended);
    }

    private Long insertRoomEvent(StudyRoom room, ResourceEventType eventType, RoomStatus afterStatus,
                                 LocalTime openTime, LocalTime closeTime, Integer rows, Integer cols,
                                 OrderPolicy policy, LocalDateTime now, Long operatorId) {
        ResourceStatusEvent event = new ResourceStatusEvent();
        event.setResourceType(ResourceType.ROOM);
        event.setEventType(eventType);
        event.setRoomId(room.getId());
        event.setSeatId(null);
        event.setAfterSnapshot(roomSnapshot(room, afterStatus, openTime, closeTime, rows, cols));
        event.setOrderPolicy(policy);
        event.setEffectiveAt(now);
        event.setOperatorId(operatorId);
        event.setCreatedAt(now);
        resourceStatusEventMapper.insert(event);
        return event.getId();
    }

    /** Effective (RESERVED/IN_USE/LEFT_TEMP) orders of every seat in the room. */
    private List<Reservation> effectiveOrdersOfRoom(Long roomId) {
        List<Reservation> orders = new ArrayList<>();
        for (Seat seat : seatMapper.selectByRoomId(roomId)) {
            orders.addAll(reservationMapper.selectEffectiveBySeatId(seat.getId()));
        }
        orders.sort((a, b) -> {
            int cmp = a.getStartAt().compareTo(b.getStartAt());
            return cmp != 0 ? cmp : a.getId().compareTo(b.getId());
        });
        return orders;
    }

    private List<Reservation> filterOutsideHours(List<Reservation> orders, LocalTime openTime,
                                                 LocalTime closeTime) {
        List<Reservation> affected = new ArrayList<>();
        for (Reservation order : orders) {
            if (!fullyWithinHours(order, openTime, closeTime)) {
                affected.add(order);
            }
        }
        return affected;
    }

    private boolean fullyWithinHours(Reservation order, LocalTime openTime, LocalTime closeTime) {
        return !order.getStartAt().toLocalTime().isBefore(openTime)
                && !order.getEndAt().toLocalTime().isAfter(closeTime);
    }

    private long countByStatus(List<Reservation> orders, ReservationStatus status) {
        return orders.stream().filter(o -> o.getStatus() == status).count();
    }

    private void refuseWhenTooMany(int effectiveCount) {
        if (effectiveCount > MAX_AFFECTED_ORDERS) {
            throw new BusinessException(ErrorCode.BUSY_RETRY,
                    "该自习室有效预约过多（" + effectiveCount + " 单），请避开高峰时段人工处理");
        }
    }

    private void validateHours(LocalTime openTime, LocalTime closeTime) {
        if (openTime == null || closeTime == null || !openTime.isBefore(closeTime)) {
            throw BusinessException.validation("开放时间必须早于关闭时间");
        }
    }

    private void validateGrid(Integer rows, Integer cols) {
        if (rows == null || cols == null || rows < 1 || rows > 100 || cols < 1 || cols > 100) {
            throw BusinessException.validation("座位行列数须在 1—100 之间");
        }
    }

    private void requireOneRow(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    action + "失败：数据已被并发修改，请刷新后重试");
        }
    }

    private void checkIdempotent(Long userId, String commandType, String requestKey, Object request) {
        if (requestKey == null || requestKey.isBlank()) {
            return;
        }
        idempotencyService.checkInTransaction(userId, commandType, requestKey, Ids.requestHash(request));
    }

    private void recordIdempotent(Long userId, String commandType, String requestKey, Object request,
                                  String resultType, String resultId) {
        if (requestKey == null || requestKey.isBlank()) {
            return;
        }
        idempotencyService.recordSuccess(userId, commandType, requestKey, Ids.requestHash(request),
                resultType, resultId);
    }

    /** LIKE 前缀通配符转义：! 自身与 %/_ 都需转义，SQL 侧以 ESCAPE '!' 声明。 */
    static String escapeLike(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private String roomSnapshot(StudyRoom room, RoomStatus afterStatus, LocalTime openTime,
                                LocalTime closeTime, Integer rows, Integer cols) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", afterStatus.name());
        snapshot.put("name", room.getName());
        snapshot.put("openTime", openTime.toString());
        snapshot.put("closeTime", closeTime.toString());
        snapshot.put("rowsCount", rows);
        snapshot.put("colsCount", cols);
        return writeJson(snapshot);
    }

    private String writeJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw BusinessException.internal("资源快照序列化失败");
        }
    }

    private RoomDetail toDetail(StudyRoom room, long totalSeats, long normalSeats) {
        return new RoomDetail(
                String.valueOf(room.getId()),
                room.getCode(),
                room.getName(),
                room.getCampus(),
                room.getBuilding(),
                room.getOpenTime(),
                room.getCloseTime(),
                room.getRowsCount(),
                room.getColsCount(),
                room.getStatus(),
                totalSeats,
                normalSeats,
                totalSeats - normalSeats,
                room.getVersion(),
                clock.toOffset(room.getCreatedAt()),
                clock.toOffset(room.getUpdatedAt()));
    }

    static SeatItem toSeatItem(Seat seat) {
        return new SeatItem(
                String.valueOf(seat.getId()),
                String.valueOf(seat.getRoomId()),
                seat.getCode(),
                seat.getRowNo(),
                seat.getColNo(),
                Boolean.TRUE.equals(seat.isHasPower()),
                Boolean.TRUE.equals(seat.isNearWindow()),
                Boolean.TRUE.equals(seat.isQuietZone()),
                seat.getStatus(),
                seat.getVersion());
    }

    private record PolicyCounters(int cancelled, int kept, int ended) {
    }
}
