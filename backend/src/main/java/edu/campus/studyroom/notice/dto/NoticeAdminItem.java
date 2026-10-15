package edu.campus.studyroom.notice.dto;

import edu.campus.studyroom.notice.domain.NoticeStatus;

import java.time.OffsetDateTime;

/**
 * Admin list row without the full content body.
 */
public record NoticeAdminItem(
        Long id,
        String title,
        NoticeStatus status,
        boolean pinned,
        OffsetDateTime publishedAt,
        OffsetDateTime updatedAt
) {
}
