package edu.campus.studyroom.notification;

import java.time.LocalDateTime;

/**
 * In-database notifications (manual 12.5). Penalty and resource-cancel notices
 * are created in the same transaction as the business change.
 */
public interface NotificationService {

    /**
     * Publishes a notification idempotently by eventKey.
     * Must run inside the current transaction (MANDATORY).
     */
    void publish(Long userId, String eventKey, String type, String title, String content,
                 Long reservationId, Long violationId, LocalDateTime publishedAt);

    long unreadCount(Long userId);
}
