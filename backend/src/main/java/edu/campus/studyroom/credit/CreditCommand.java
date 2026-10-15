package edu.campus.studyroom.credit;

import edu.campus.studyroom.credit.domain.CreditLogType;

/**
 * Credit event command. eventKey examples (manual 7.2.7):
 * reservation:123:terminal / reservation:123:reward / violation:456:refund / manual:uuid
 */
public record CreditCommand(
        String eventKey,
        Long userId,
        int requestedDelta,
        CreditLogType type,
        Long reservationId,
        Long violationId,
        Long sourceLogId,
        String reason,
        Long operatorId
) {
    public static CreditCommand of(String eventKey, Long userId, int requestedDelta, CreditLogType type,
                                   Long reservationId, String reason) {
        return new CreditCommand(eventKey, userId, requestedDelta, type, reservationId, null, null, reason, null);
    }
}
