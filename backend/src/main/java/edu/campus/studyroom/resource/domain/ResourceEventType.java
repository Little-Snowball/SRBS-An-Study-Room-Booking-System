package edu.campus.studyroom.resource.domain;

/** Resource change kind; status history is rebuilt from these snapshots. */
public enum ResourceEventType {
    CREATED, STATUS_CHANGED, HOURS_CHANGED
}
