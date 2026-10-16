package edu.campus.studyroom.user.dto;

import edu.campus.studyroom.user.domain.UserRole;
import edu.campus.studyroom.user.domain.UserStatus;

import java.time.OffsetDateTime;

/**
 * Admin user list item. IDs are strings; date-times carry a +08:00 offset.
 * Never carries password hashes or any other secret.
 */
public record AdminUserItem(
        String id,
        String account,
        String name,
        String college,
        UserRole role,
        UserStatus status,
        boolean blocked,
        int creditScore,
        int authVersion,
        OffsetDateTime createdAt
) {
}
