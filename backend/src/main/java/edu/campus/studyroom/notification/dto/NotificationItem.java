package edu.campus.studyroom.notification.dto;

import java.time.OffsetDateTime;

/**
 * 站内通知列表项。ID 一律字符串（JS 安全整数）；publishedAt 带 +08:00 偏移。
 */
public record NotificationItem(
        String id,
        String type,
        String title,
        String content,
        String reservationId,
        String violationId,
        boolean read,
        OffsetDateTime publishedAt
) {
}
