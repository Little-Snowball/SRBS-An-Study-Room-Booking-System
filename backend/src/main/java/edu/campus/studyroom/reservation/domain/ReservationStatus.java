package edu.campus.studyroom.reservation.domain;

/** Reservation lifecycle. Terminal states never return to an effective state. */
public enum ReservationStatus {
    RESERVED, IN_USE, LEFT_TEMP, COMPLETED, CANCELLED, VIOLATED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == VIOLATED;
    }

    public boolean isEffective() {
        return this == RESERVED || this == IN_USE || this == LEFT_TEMP;
    }
}
