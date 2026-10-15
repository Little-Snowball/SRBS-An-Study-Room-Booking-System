package edu.campus.studyroom.attendance.controller;

import edu.campus.studyroom.attendance.dto.AttendanceResult;
import edu.campus.studyroom.attendance.dto.CheckInRequest;
import edu.campus.studyroom.attendance.service.AttendanceCommandService;
import edu.campus.studyroom.attendance.service.AttendanceQueryService;
import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.IdempotencyService;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.resource.domain.StudyRoom;
import edu.campus.studyroom.resource.mapper.StudyRoomMapper;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import edu.campus.studyroom.security.PasswordPolicy;
import edu.campus.studyroom.security.RateLimiter;
import edu.campus.studyroom.settlement.PreflightSettlementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Attendance commands on an existing reservation. Every endpoint:
 * 1. runs the pre-flight settlement in its own committed transaction first, so a
 *    due penalty lands before the command re-validates and can never be rolled
 *    back by a later business rejection;
 * 2. requires an Idempotency-Key header; a same-key replay re-fetches the current
 *    view (settlement values replayed from settlement_result for check-out);
 * 3. is restricted to the owning student — the service re-checks ownership and
 *    answers 404 for anyone else, so admins cannot check in for a student.
 */
@RestController
@RequestMapping("/api/v1/reservations")
public class AttendanceController {

    private final AttendanceCommandService commandService;
    private final AttendanceQueryService queryService;
    private final PreflightSettlementService preflightSettlementService;
    private final ReservationMapper reservationMapper;
    private final StudyRoomMapper studyRoomMapper;
    private final RateLimiter rateLimiter;
    private final PasswordPolicy passwordPolicy;
    private final BusinessClock clock;

    public AttendanceController(AttendanceCommandService commandService,
                                AttendanceQueryService queryService,
                                PreflightSettlementService preflightSettlementService,
                                ReservationMapper reservationMapper,
                                StudyRoomMapper studyRoomMapper,
                                RateLimiter rateLimiter,
                                PasswordPolicy passwordPolicy,
                                BusinessClock clock) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.preflightSettlementService = preflightSettlementService;
        this.reservationMapper = reservationMapper;
        this.studyRoomMapper = studyRoomMapper;
        this.rateLimiter = rateLimiter;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
    }

    /** No-fault cancel inside the cancel window. */
    @PostMapping("/{id}/cancel")
    public ApiResponse<AttendanceResult> cancel(@PathVariable String id,
                                                @RequestHeader("Idempotency-Key") String requestKey,
                                                @CurrentUser CurrentUserPrincipal user) {
        Long reservationId = parseId(id);
        preflightSettlementService.preflightForOrder(reservationId, clock.nowLocal());
        try {
            return ApiResponse.ok(commandService.cancel(reservationId, user.userId(), requestKey));
        } catch (IdempotencyService.IdempotentReplayException replay) {
            return ApiResponse.ok(queryService.replayCheckout(reservationId, user.userId()));
        }
    }

    /**
     * Check-in with the six-digit on-site code. The code is verified with BCrypt
     * OUTSIDE the command transaction against the room row read here; the rate
     * limit gates the expensive hash check, and a wrong code records a failure
     * without touching the order.
     */
    @PostMapping("/{id}/check-in")
    public ApiResponse<AttendanceResult> checkIn(@PathVariable String id,
                                                 @RequestHeader("Idempotency-Key") String requestKey,
                                                 @Valid @RequestBody CheckInRequest request,
                                                 @CurrentUser CurrentUserPrincipal user,
                                                 HttpServletRequest httpRequest) {
        Long reservationId = parseId(id);
        Long userId = user.userId();
        String ip = httpRequest.getRemoteAddr();

        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null || !reservation.getUserId().equals(userId)) {
            throw BusinessException.notFound("预约不存在或无权访问");
        }
        StudyRoom room = studyRoomMapper.selectById(reservation.getRoomId());
        if (room == null) {
            throw BusinessException.notFound("自习室不存在");
        }

        rateLimiter.checkCheckinAllowed(userId, reservationId, ip);
        if (!passwordPolicy.matches(request.code(), room.getCheckinCodeHash())) {
            rateLimiter.recordCheckinFailure(userId, reservationId, ip);
            throw new BusinessException(ErrorCode.INVALID_CHECKIN_CODE, "现场码不正确，请核对后重试");
        }

        preflightSettlementService.preflightForOrder(reservationId, clock.nowLocal());
        try {
            return ApiResponse.ok(commandService.checkIn(reservationId, userId, request.code(),
                    requestKey, room.getVersion(), room.getCheckinCodeHash()));
        } catch (IdempotencyService.IdempotentReplayException replay) {
            return ApiResponse.ok(queryService.replayCheckout(reservationId, userId));
        }
    }

    /** Temporary leave (at most leaveMaxCount per order). */
    @PostMapping("/{id}/leave")
    public ApiResponse<AttendanceResult> leave(@PathVariable String id,
                                               @RequestHeader("Idempotency-Key") String requestKey,
                                               @CurrentUser CurrentUserPrincipal user) {
        Long reservationId = parseId(id);
        preflightSettlementService.preflightForOrder(reservationId, clock.nowLocal());
        try {
            return ApiResponse.ok(commandService.leave(reservationId, user.userId(), requestKey));
        } catch (IdempotencyService.IdempotentReplayException replay) {
            return ApiResponse.ok(queryService.replayCheckout(reservationId, user.userId()));
        }
    }

    /** Return from a temporary leave, allowed exactly at the leave deadline. */
    @PostMapping("/{id}/return")
    public ApiResponse<AttendanceResult> returnFromLeave(@PathVariable String id,
                                                         @RequestHeader("Idempotency-Key") String requestKey,
                                                         @CurrentUser CurrentUserPrincipal user) {
        Long reservationId = parseId(id);
        preflightSettlementService.preflightForOrder(reservationId, clock.nowLocal());
        try {
            return ApiResponse.ok(commandService.returnFromLeave(reservationId, user.userId(), requestKey));
        } catch (IdempotencyService.IdempotentReplayException replay) {
            return ApiResponse.ok(queryService.replayCheckout(reservationId, user.userId()));
        }
    }

    /** Check-out; at or after end it degrades to AUTO_END with zero reward. */
    @PostMapping("/{id}/check-out")
    public ApiResponse<AttendanceResult> checkOut(@PathVariable String id,
                                                  @RequestHeader("Idempotency-Key") String requestKey,
                                                  @CurrentUser CurrentUserPrincipal user) {
        Long reservationId = parseId(id);
        preflightSettlementService.preflightForOrder(reservationId, clock.nowLocal());
        try {
            return ApiResponse.ok(commandService.checkOut(reservationId, user.userId(), requestKey));
        } catch (IdempotencyService.IdempotentReplayException replay) {
            return ApiResponse.ok(queryService.replayCheckout(reservationId, user.userId()));
        }
    }

    private static Long parseId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw BusinessException.validation("预约 ID 不合法");
        }
    }
}
