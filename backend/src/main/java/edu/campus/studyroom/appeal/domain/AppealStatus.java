package edu.campus.studyroom.appeal.domain;

/** Appeal lifecycle. PENDING is unique per violation; REJECTED cannot be resubmitted. */
public enum AppealStatus {
    PENDING, APPROVED, REJECTED
}
