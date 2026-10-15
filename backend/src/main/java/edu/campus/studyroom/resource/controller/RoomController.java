package edu.campus.studyroom.resource.controller;

import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.resource.dto.RoomAvailability;
import edu.campus.studyroom.resource.dto.RoomDetail;
import edu.campus.studyroom.resource.dto.RoomListItem;
import edu.campus.studyroom.resource.service.AvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Public/authenticated room queries. Results never lock a seat; a submitted
 * reservation can still conflict (409).
 */
@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final AvailabilityService availabilityService;

    public RoomController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public ApiResponse<PageResult<RoomListItem>> list(
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) String building,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) OffsetDateTime startAt,
            @RequestParam(required = false) OffsetDateTime endAt,
            @RequestParam(required = false) Boolean hasPower,
            @RequestParam(required = false) Boolean nearWindow,
            @RequestParam(required = false) Boolean quietZone,
            PageQuery pageQuery) {
        LocalDateTime start = startAt == null ? null : startAt.atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        LocalDateTime end = endAt == null ? null : endAt.atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        return ApiResponse.ok(availabilityService.listRooms(campus, building, date, start, end,
                hasPower, nearWindow, quietZone, pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(availabilityService.roomDetail(id));
    }

    @GetMapping("/{id}/availability")
    public ApiResponse<RoomAvailability> availability(
            @PathVariable Long id,
            @RequestParam OffsetDateTime startAt,
            @RequestParam OffsetDateTime endAt) {
        LocalDateTime start = startAt.atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        LocalDateTime end = endAt.atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        return ApiResponse.ok(availabilityService.availability(id, start, end));
    }
}
