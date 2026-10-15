package edu.campus.studyroom.reservation.domain;

import edu.campus.studyroom.rule.domain.RuleConfig;

import java.time.LocalDateTime;

/**
 * Deadline derivation rules (manual chapter 3 and 9). Pure functions so they can
 * be unit-tested without a database; all comparisons are half-open [start, end).
 */
public final class DeadlineRules {

    private DeadlineRules() {
    }

    /**
     * Effective check-in deadline: min(start + checkinLateMinutes, end).
     * end itself can never be a check-in moment; an order past end cannot be
     * revived by a late check-in.
     */
    public static LocalDateTime checkinDue(LocalDateTime start, RuleConfig rule, LocalDateTime end) {
        LocalDateTime grace = start.plusMinutes(rule.checkinLateMinutes());
        return grace.isBefore(end) ? grace : end;
    }

    /** No-fault cancel deadline: start - cancelBeforeMinutes. */
    public static LocalDateTime cancelDue(LocalDateTime start, RuleConfig rule) {
        return start.minusMinutes(rule.cancelBeforeMinutes());
    }

    /** Early check-in window opens at start - checkinEarlyMinutes. */
    public static LocalDateTime checkinOpensAt(LocalDateTime start, RuleConfig rule) {
        return start.minusMinutes(rule.checkinEarlyMinutes());
    }

    /** Leave deadline: last_leave_at + leaveMaxMinutes. */
    public static LocalDateTime leaveDueAt(LocalDateTime lastLeaveAt, RuleConfig rule) {
        return lastLeaveAt.plusMinutes(rule.leaveMaxMinutes());
    }

    /**
     * The earliest applicable business deadline of an effective order, or null
     * when nothing is due yet. RESERVED uses checkin_due_at; IN_USE has none;
     * LEFT_TEMP uses leave_due_at. Equal deadlines prefer END.
     */
    public static LocalDateTime earliestDue(ReservationStatus status, LocalDateTime checkinDueAt,
                                            LocalDateTime leaveDueAt, LocalDateTime endAt) {
        return switch (status) {
            case RESERVED -> checkinDueAt;
            case LEFT_TEMP -> leaveDueAt;
            case IN_USE -> null;
            default -> null;
        };
    }

    /** No-show applies when now > checkinDue and that deadline is before end. */
    public static boolean noShowDue(LocalDateTime now, LocalDateTime checkinDueAt, LocalDateTime endAt) {
        return now.isAfter(checkinDueAt) && checkinDueAt.isBefore(endAt);
    }

    /** Leave-timeout applies when now > leaveDue and that deadline is before end. */
    public static boolean leaveTimeoutDue(LocalDateTime now, LocalDateTime leaveDueAt, LocalDateTime endAt) {
        return leaveDueAt != null && now.isAfter(leaveDueAt) && leaveDueAt.isBefore(endAt);
    }

    /** Auto-complete applies when now >= end. */
    public static boolean endReached(LocalDateTime now, LocalDateTime endAt) {
        return !now.isBefore(endAt);
    }
}
