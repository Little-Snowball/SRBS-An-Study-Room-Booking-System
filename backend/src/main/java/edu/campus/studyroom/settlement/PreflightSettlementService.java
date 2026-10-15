package edu.campus.studyroom.settlement;

import java.time.LocalDateTime;

/**
 * Pre-flight settlement. Runs in its own short transaction and commits BEFORE the
 * command transaction re-validates, so a later business rejection never rolls
 * back a due penalty (manual 8.2). Callers: controllers before reservation
 * creation, check-in, leave, return and checkout commands.
 */
public interface PreflightSettlementService {

    /** Settles due orders of the target seat and the acting user. */
    void preflightForReservationCreate(Long seatId, Long userId, LocalDateTime now);

    /** Settles the order itself when it is due. */
    void preflightForOrder(Long reservationId, LocalDateTime now);
}
