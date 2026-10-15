package edu.campus.studyroom.appeal.dto;

import edu.campus.studyroom.appeal.domain.AppealStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Appeal detail with the violation summary, the student reason, bound evidence
 * ids and the review outcome.
 */
public record AppealDetail(
        Long id,
        Long violationId,
        Long reservationId,
        String violationReason,
        OffsetDateTime violationEventAt,
        Integer deducted,
        String reason,
        List<Long> fileIds,
        AppealStatus status,
        String reviewComment,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewedAt,
        Long reviewerId
) {
}
