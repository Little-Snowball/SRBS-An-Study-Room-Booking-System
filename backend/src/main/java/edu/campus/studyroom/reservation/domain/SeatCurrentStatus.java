package edu.campus.studyroom.reservation.domain;

/** Derived current status of a seat right now (availability view). */
public enum SeatCurrentStatus {
    FREE, RESERVED, IN_USE, LEFT_TEMP, MAINTENANCE, CLOSED
}
