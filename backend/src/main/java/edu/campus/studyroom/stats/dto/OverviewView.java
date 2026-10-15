package edu.campus.studyroom.stats.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 运营概览的扁平化响应（前端统计日期与指标同层）。
 * 保留全部原始分子分母，便于手工核算；空分母返回 null 而不是 0。
 */
public record OverviewView(
        LocalDate date,
        long todayBookings,
        long cancelledCount,
        long distinctStudentCount,
        Double occupancyRate,
        long occupancyNumerator,
        long occupancyDenominator,
        long keptActiveSeats,
        OffsetDateTime weekStart,
        OffsetDateTime weekEnd,
        OffsetDateTime asOfAt,
        Double violationRate,
        long violationNumerator,
        long violationDenominator,
        long settlementPendingCount
) {
    public static OverviewView from(OverviewResult result) {
        OccupancyView occupancy = result.occupancy();
        WeekViolationView week = result.weekViolation();
        return new OverviewView(
                result.date().toLocalDate(),
                result.todayReservationCount(),
                result.cancelledCount(),
                result.distinctStudentCount(),
                occupancy == null ? null : occupancy.rate(),
                occupancy == null ? 0 : occupancy.numerator(),
                occupancy == null ? 0 : occupancy.denominator(),
                occupancy == null ? 0 : occupancy.keptActiveSeats(),
                week == null ? null : week.weekStart(),
                week == null ? null : week.weekEnd(),
                week == null ? null : week.asOfAt(),
                week == null ? null : week.rate(),
                week == null ? 0 : week.numerator(),
                week == null ? 0 : week.denominator(),
                week == null ? 0 : week.settlementPendingCount());
    }
}
