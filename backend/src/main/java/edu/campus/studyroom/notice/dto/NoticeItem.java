package edu.campus.studyroom.notice.dto;

import java.time.OffsetDateTime;

/**
 * Public notice view: plain text only, rendered with client-side escaping.
 */
public record NoticeItem(
        Long id,
        String title,
        String content,
        boolean pinned,
        OffsetDateTime publishedAt
) {
}
