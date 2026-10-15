package edu.campus.studyroom.credit;

import edu.campus.studyroom.credit.domain.CreditLogType;

/**
 * The single credit-event entry point (manual 12.5). ViolationService and
 * SettlementService only deduct through this interface; appeal refunds and
 * manual adjustments use the same path. Must be invoked inside a guarded
 * transaction with the user's credit_account row locked.
 */
public interface CreditService {

    /**
     * Applies one credit event idempotently by eventKey.
     * after = clamp(before + requested, 0, 100); actual = after - before;
     * capped = requested - actual. A zero-change event still writes a log row.
     */
    CreditResult apply(CreditCommand command);

    /** Current score without locking (queries and personal overview). */
    int currentScore(Long userId);
}
