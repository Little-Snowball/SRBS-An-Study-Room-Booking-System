package edu.campus.studyroom.settlement;

import edu.campus.studyroom.reservation.domain.TerminationReason;

import java.time.LocalDateTime;

/**
 * Terminal settlement (manual 8.5/9). One settlement per order; the same service
 * is used by the scheduler, by pre-flight settlement before commands and inside
 * commands that hit a deadline mid-flight. Every settlement writes the terminal
 * attendance event, settlement_result and (for violations) violation + credit +
 * notification in one transaction.
 */
public interface SettlementService {

    /**
     * Settles one reservation by id inside a guarded transaction.
     * Re-reads the order and the clock; the candidate list is never a state basis.
     *
     * @param trigger SCHEDULER / PREFIGHT / COMMAND for audit purposes
     * @return the settlement outcome, or null when nothing was due
     */
    SettlementOutcome settleOne(Long reservationId, LocalDateTime now, String trigger);

    /** Batch entry for the scheduler: settle due candidates up to limit. */
    int settleBatch(LocalDateTime now, int limit);

    /**
     * Student checkout before end: COMPLETED with reward by actual in-seat seconds.
     * When now >= end it degrades to the AUTO_END settlement (zero reward).
     */
    SettlementOutcome checkout(Long reservationId, LocalDateTime now);

    /** No-fault student cancel inside the cancel window: CANCELLED, zero reward/penalty. */
    SettlementOutcome cancel(Long reservationId, LocalDateTime now);

    /**
     * Admin no-fault termination: RESERVED becomes CANCELLED, IN_USE/LEFT_TEMP
     * become COMPLETED with zero reward and zero penalty.
     */
    SettlementOutcome adminTerminate(Long reservationId, LocalDateTime now, TerminationReason reason);
}
