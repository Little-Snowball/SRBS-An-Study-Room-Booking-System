package edu.campus.studyroom.file.domain;

/** File object state; TEMP files older than 24h may be cleaned by maintenance. */
public enum FileState {
    TEMP, BOUND, QUARANTINED
}
