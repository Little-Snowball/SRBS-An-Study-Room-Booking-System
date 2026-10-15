package edu.campus.studyroom.notice.domain;

/** Notice lifecycle; only PUBLISHED notices are visible publicly. */
public enum NoticeStatus {
    DRAFT, PUBLISHED, WITHDRAWN
}
