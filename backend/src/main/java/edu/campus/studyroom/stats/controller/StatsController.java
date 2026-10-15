package edu.campus.studyroom.stats.controller;

import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.stats.dto.OverviewResult;
import edu.campus.studyroom.stats.dto.OverviewView;
import edu.campus.studyroom.stats.dto.RoomUsageRow;
import edu.campus.studyroom.stats.dto.TrafficResult;
import edu.campus.studyroom.stats.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Admin statistics endpoints (manual chapter 14). All reads return raw
 * numerator/denominator values for hand verification. The usage ranking pages
 * with size 10 by default.
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public ApiResponse<OverviewView> overview(@RequestParam(required = false) LocalDate date) {
        return ApiResponse.ok(OverviewView.from(statsService.overview(date)));
    }

    @GetMapping("/traffic")
    public ApiResponse<TrafficResult> traffic(@RequestParam(required = false) LocalDate date,
                                              @RequestParam(required = false) Long roomId) {
        return ApiResponse.ok(statsService.traffic(date, roomId));
    }

    @GetMapping("/room-usage")
    public ApiResponse<PageResult<RoomUsageRow>> roomUsage(
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ApiResponse.ok(statsService.roomUsage(dateFrom, dateTo, new PageQuery(page, size, null)));
    }
}
