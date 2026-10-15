package edu.campus.studyroom.reservation.domain;

/** Terminal reason; distinguishes same-name events (e.g. CHECK_OUT vs FORCE_END). */
public enum TerminationReason {
    STUDENT_CANCEL, STUDENT_CHECKOUT, AUTO_END, NO_SHOW, LEAVE_TIMEOUT,
    ROOM_CLOSED, SEAT_MAINTENANCE, HOURS_CHANGED, ACCOUNT_DISABLED
}
