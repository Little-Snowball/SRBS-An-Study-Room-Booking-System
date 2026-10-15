package edu.campus.studyroom.user.dto;

import edu.campus.studyroom.credit.domain.CreditLogType;

import java.time.OffsetDateTime;

/**
 * One credit log row for the personal center. requested/actual/capped expose the
 * clamping that CreditService applied; IDs are strings and the timestamp carries
 * a +08:00 offset.
 */
public record CreditLogItem(
        String id,
        CreditLogType type,
        int requestedDelta,
        int actualDelta,
        int cappedDelta,
        int balanceBefore,
        int balanceAfter,
        String reason,
        String reservationId,
        String violationId,
        OffsetDateTime createdAt
) {
}
