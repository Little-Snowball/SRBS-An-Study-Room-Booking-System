package edu.campus.studyroom.resource.domain;

/** How existing effective orders are handled when a room/seat closes or enters maintenance. */
public enum OrderPolicy {
    KEEP_ACTIVE, FORCE_END
}
