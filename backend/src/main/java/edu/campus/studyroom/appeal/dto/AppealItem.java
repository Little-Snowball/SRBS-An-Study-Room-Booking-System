package edu.campus.studyroom.appeal.dto;

import edu.campus.studyroom.appeal.domain.AppealStatus;

import java.time.OffsetDateTime;

/**
 * Appeal list row with a violation summary (reason, event time, deducted score).
 */
public record AppealItem(
        Long id,
        Long violationId,
        String violationReason,
        OffsetDateTime violationEventAt,
        Integer deducted,
        AppealStatus status,
        String reviewComment,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewedAt
) {
}
