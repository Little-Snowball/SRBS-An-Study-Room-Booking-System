package edu.campus.studyroom.auth.dto;

import java.time.OffsetDateTime;

/**
 * Login response: bearer token, its expiry (business zone offset) and the
 * current user profile.
 */
public record LoginResponse(String accessToken, OffsetDateTime expiresAt,
                            UserProfile user) {
}
