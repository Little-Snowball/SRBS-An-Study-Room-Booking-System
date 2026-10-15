package edu.campus.studyroom.resource.controller;

import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.resource.dto.*;
import edu.campus.studyroom.resource.service.RoomService;
import edu.campus.studyroom.resource.service.SeatService;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin room/seat maintenance. Closing a room, shortening hours or disabling a
 * seat requires an order policy and a reason; the UI previews the impact first.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminRoomController {

    private final RoomService roomService;
    private final SeatService seatService;

    public AdminRoomController(RoomService roomService, SeatService seatService) {
        this.roomService = roomService;
        this.seatService = seatService;
    }

    // ---------- rooms ----------

    @GetMapping("/rooms")
    public ApiResponse<PageResult<RoomListItem>> rooms(PageQuery pageQuery) {
        return ApiResponse.ok(roomService.pageRooms(pageQuery));
    }

    @PostMapping("/rooms")
    public ApiResponse<RoomDetail> create(@CurrentUser CurrentUserPrincipal admin,
                                          @RequestBody @Valid CreateRoomRequest request) {
        return ApiResponse.created(roomService.create(admin, request));
    }

    @PatchMapping("/rooms/{id}")
    public ApiResponse<RoomDetail> update(@CurrentUser CurrentUserPrincipal admin,
                                          @PathVariable Long id,
                                          @RequestBody @Valid UpdateRoomRequest request) {
        return ApiResponse.ok(roomService.updateBasics(admin, id, request));
    }

    @PostMapping("/rooms/{id}/status")
    public ApiResponse<edu.campus.studyroom.resource.dto.RoomStatusResult> status(@CurrentUser CurrentUserPrincipal admin,
                                                             @PathVariable Long id,
                                                             @RequestBody @Valid RoomStatusRequest request) {
        return ApiResponse.ok(roomService.updateStatus(admin, id, request));
    }

    @PostMapping("/rooms/{id}/checkin-code")
    public ApiResponse<Void> checkinCode(@CurrentUser CurrentUserPrincipal admin,
                                         @PathVariable Long id,
                                         @RequestBody @Valid CheckinCodeRequest request) {
        roomService.updateCheckinCode(admin, id, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/rooms/{id}/seats")
    public ApiResponse<SeatLayout> seats(@PathVariable Long id) {
        return ApiResponse.ok(new SeatLayout(String.valueOf(id), seatService.layout(id)));
    }

    @PostMapping("/rooms/{id}/seats/batch")
    public ApiResponse<BatchSeatResult> batchSeats(@CurrentUser CurrentUserPrincipal admin,
                                                   @PathVariable Long id,
                                                   @RequestBody @Valid BatchSeatRequest request) {
        int generated = seatService.batchGenerate(admin, id, request);
        return ApiResponse.created(new BatchSeatResult(String.valueOf(id), generated));
    }

    @GetMapping("/rooms/{id}/impact")
    public ApiResponse<RoomImpact> roomImpact(@PathVariable Long id,
                                              @RequestParam(required = false) String newStatus) {
        return ApiResponse.ok(roomService.impact(id, newStatus));
    }

    // ---------- seats ----------

    @PatchMapping("/seats/{id}")
    public ApiResponse<SeatItem> updateSeat(@CurrentUser CurrentUserPrincipal admin,
                                            @PathVariable Long id,
                                            @RequestBody @Valid UpdateSeatRequest request) {
        return ApiResponse.ok(seatService.updateLabels(admin, id, request));
    }

    @PostMapping("/seats/{id}/status")
    public ApiResponse<SeatService.SeatStatusOutcome> seatStatus(@CurrentUser CurrentUserPrincipal admin,
                                                                 @PathVariable Long id,
                                                                 @RequestBody @Valid SeatStatusRequest request) {
        return ApiResponse.ok(seatService.updateStatus(admin, id, request));
    }

    @GetMapping("/seats/{id}/impact")
    public ApiResponse<SeatImpact> seatImpact(@PathVariable Long id,
                                              @RequestParam(required = false) String newStatus) {
        return ApiResponse.ok(seatService.impact(id, newStatus));
    }
}
