package edu.campus.studyroom.security;

import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.user.domain.UserRole;
import edu.campus.studyroom.user.domain.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Authenticated principal resolved per request from the database.
 * Role and status are read from the current user row, never from stale token claims.
 */
public record CurrentUserPrincipal(Long userId, String account, String name, UserRole role,
                                   UserStatus status, boolean blocked) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isStudent() {
        return role == UserRole.STUDENT;
    }

    public static String newSessionId() {
        return UUID.randomUUID().toString();
    }

    public static LocalDateTime expiryFrom(BusinessClock clock, int hours) {
        return LocalDateTime.ofInstant(clock.now().plusSeconds(hours * 3600L), clock.zone());
    }
}
