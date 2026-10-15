package edu.campus.studyroom.reservation.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Command-allowed transition matrix (manual chapter 9). Buttons on the UI are
 * hints only; the server always decides. Repeated successful commands on a
 * terminal order are idempotent replays, other transitions get INVALID_STATE.
 */
public final class ReservationStateMachine {

    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED = new EnumMap<>(ReservationStatus.class);

    static {
        ALLOWED.put(ReservationStatus.RESERVED,
                Set.of(ReservationStatus.IN_USE, ReservationStatus.CANCELLED, ReservationStatus.VIOLATED,
                        ReservationStatus.COMPLETED));
        ALLOWED.put(ReservationStatus.IN_USE,
                Set.of(ReservationStatus.LEFT_TEMP, ReservationStatus.COMPLETED, ReservationStatus.VIOLATED));
        ALLOWED.put(ReservationStatus.LEFT_TEMP,
                Set.of(ReservationStatus.IN_USE, ReservationStatus.COMPLETED, ReservationStatus.VIOLATED));
        ALLOWED.put(ReservationStatus.COMPLETED, Set.of());
        ALLOWED.put(ReservationStatus.CANCELLED, Set.of());
        ALLOWED.put(ReservationStatus.VIOLATED, Set.of());
    }

    private ReservationStateMachine() {
    }

    public static boolean canTransition(ReservationStatus from, ReservationStatus to) {
        Set<ReservationStatus> targets = ALLOWED.get(from);
        return targets != null && targets.contains(to);
    }

    /** Effective (non-terminal) statuses that hold seat usage rights. */
    public static boolean isEffective(ReservationStatus status) {
        return status != null && status.isEffective();
    }

    public static boolean isTerminal(ReservationStatus status) {
        return status != null && status.isTerminal();
    }
}
