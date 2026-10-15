package edu.campus.studyroom.reservation.controller;

import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.IdempotencyService;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.dto.CreateReservationRequest;
import edu.campus.studyroom.reservation.dto.ReservationDetail;
import edu.campus.studyroom.reservation.dto.ReservationListItem;
import edu.campus.studyroom.reservation.service.ReservationCommandService;
import edu.campus.studyroom.reservation.service.ReservationQueryService;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import edu.campus.studyroom.settlement.PreflightSettlementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Reservation endpoints. Pre-flight settlement runs in its own committed
 * transaction BEFORE the command transaction so a due penalty is never rolled
 * back by a later rejection. Idempotent replays re-fetch the current view under
 * a fresh permission check instead of replaying stale data.
 */
@RestController
public class ReservationController {

    private final ReservationCommandService commandService;
    private final ReservationQueryService queryService;
    private final PreflightSettlementService preflightSettlementService;
    private final BusinessClock clock;

    public ReservationController(ReservationCommandService commandService,
                                 ReservationQueryService queryService,
                                 PreflightSettlementService preflightSettlementService,
                                 BusinessClock clock) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.preflightSettlementService = preflightSettlementService;
        this.clock = clock;
    }

    /** Create a reservation (students only). Requires Idempotency-Key header. */
    @PostMapping("/api/v1/reservations")
    public ApiResponse<ReservationDetail> create(@RequestHeader("Idempotency-Key") String requestKey,
                                                 @Valid @RequestBody CreateReservationRequest request,
                                                 @CurrentUser CurrentUserPrincipal user) {
        if (!user.isStudent()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅学生可以预约自习室");
        }
        Long seatId = parseId(request.seatId(), "座位 ID");
        preflightSettlementService.preflightForReservationCreate(seatId, user.userId(), clock.nowLocal());
        try {
            ReservationDetail detail = commandService.createReservation(user.userId(), request, requestKey);
            return ApiResponse.created(detail);
        } catch (IdempotencyService.IdempotentReplayException replay) {
            ReservationDetail detail = queryService.getDetailForUser(
                    parseId(replay.getResultId(), "预约 ID"), user.userId(), false);
            return ApiResponse.ok(detail);
        }
    }

    /** Detail of one reservation: the owning student or any admin. */
    @GetMapping("/api/v1/reservations/{id}")
    public ApiResponse<ReservationDetail> detail(@PathVariable String id,
                                                 @CurrentUser CurrentUserPrincipal user) {
        Long reservationId = parseId(id, "预约 ID");
        return ApiResponse.ok(queryService.getDetailForUser(reservationId, user.userId(), user.isAdmin()));
    }

    /** My reservations (students), optionally filtered by status and start-date range. */
    @GetMapping("/api/v1/me/reservations")
    public ApiResponse<PageResult<ReservationListItem>> listMine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @CurrentUser CurrentUserPrincipal user) {
        if (!user.isStudent()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅学生可以查看我的预约");
        }
        ReservationStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
        PageQuery pageQuery = new PageQuery(page, size, null);
        PageResult<ReservationListItem> result = queryService.listMine(
                user.userId(), statusFilter, dateFrom, dateTo, pageQuery);
        return ApiResponse.ok(result);
    }

    /**
     * Admin reservation query (read-only, ROLE_ADMIN via security config).
     * Filters: account prefix, room, status, start-date range. No proxy
     * check-in is exposed here — attendance commands stay owner-only.
     */
    @GetMapping("/api/v1/admin/reservations")
    public ApiResponse<PageResult<ReservationListItem>> listAdmin(
            @RequestParam(required = false) String account,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        ReservationStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
        PageQuery pageQuery = new PageQuery(page, size, null);
        return ApiResponse.ok(queryService.listAdmin(account, roomId, statusFilter, dateFrom, dateTo, pageQuery));
    }

    private static ReservationStatus parseStatus(String status) {
        try {
            return ReservationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.validation("status 参数不合法，须为 RESERVED/IN_USE/LEFT_TEMP/COMPLETED/CANCELLED/VIOLATED");
        }
    }

    private static Long parseId(String raw, String label) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw BusinessException.validation(label + " 不合法");
        }
    }
}
