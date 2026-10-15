package edu.campus.studyroom.notice.dto;

import edu.campus.studyroom.notice.domain.NoticeStatus;

import java.time.OffsetDateTime;

/**
 * Admin detail view of one notice, including lifecycle state and version.
 */
public record NoticeDetail(
        Long id,
        String title,
        String content,
        boolean pinned,
        NoticeStatus status,
        OffsetDateTime publishedAt,
        Long createdBy,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
