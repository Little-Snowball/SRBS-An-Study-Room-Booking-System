package edu.campus.studyroom.stats.dto;

import java.time.OffsetDateTime;

/**
 * Admin dashboard overview for one date. Reservation counts are start-date
 * attributed and never deduplicated by student; the distinct-student count is
 * reported separately.
 */
public record OverviewResult(
        OffsetDateTime date,
        long todayReservationCount,
        long cancelledCount,
        long distinctStudentCount,
        OccupancyView occupancy,
        WeekViolationView weekViolation
) {
}
