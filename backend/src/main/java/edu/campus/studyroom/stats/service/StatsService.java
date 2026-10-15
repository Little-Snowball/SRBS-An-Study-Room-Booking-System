package edu.campus.studyroom.stats.service;

import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.stats.dto.OverviewResult;
import edu.campus.studyroom.stats.dto.RoomUsageRow;
import edu.campus.studyroom.stats.dto.TrafficResult;

import java.time.LocalDate;

/**
 * Read-only admin statistics (manual chapter 14). Every endpoint returns the
 * raw numerator/denominator values so results can be verified by hand.
 */
public interface StatsService {

    /** Dashboard overview for a date (defaults to today). */
    OverviewResult overview(LocalDate date);

    /** 30-minute check-in buckets for a date, optionally filtered by room. */
    TrafficResult traffic(LocalDate date, Long roomId);

    /** Per-room usage ranking over a range of at most 31 days. */
    PageResult<RoomUsageRow> roomUsage(LocalDate dateFrom, LocalDate dateTo, PageQuery pageQuery);
}
