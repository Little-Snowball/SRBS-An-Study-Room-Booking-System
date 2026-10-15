package edu.campus.studyroom.reservation.domain;

/** Attendance event kinds; only successful transitions write events. */
public enum AttendanceEventType {
    CHECK_IN, LEAVE, RETURN, CHECK_OUT, AUTO_END, FORCE_END, VIOLATION_END, CANCEL
}
