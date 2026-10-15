package edu.campus.studyroom.settlement;

import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.domain.TerminationReason;

/**
 * Outcome of one settlement. alreadyTerminal=true means a previous settlement
 * exists and its stored values are replayed (idempotent checkout).
 */
public record SettlementOutcome(
        Long reservationId,
        ReservationStatus status,
        TerminationReason reason,
        boolean alreadyTerminal,
        long eligibleSeconds,
        int rewardRequested,
        int rewardActual
) {
}
