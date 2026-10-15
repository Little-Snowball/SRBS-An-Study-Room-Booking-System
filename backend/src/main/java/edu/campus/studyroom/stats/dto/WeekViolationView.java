package edu.campus.studyroom.stats.dto;

import java.time.OffsetDateTime;

/**
 * Weekly violation rate. Denominator counts non-cancelled orders of the week
 * that are terminal or already past their end at asOfAt; numerator counts those
 * with an ACTIVE violation (revoked penalties excluded).
 */
public record WeekViolationView(
        OffsetDateTime weekStart,
        OffsetDateTime weekEnd,
        OffsetDateTime asOfAt,
        long numerator,
        long denominator,
        Double rate,
        long settlementPendingCount
) {
}
