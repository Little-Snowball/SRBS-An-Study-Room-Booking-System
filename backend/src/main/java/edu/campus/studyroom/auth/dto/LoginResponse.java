package edu.campus.studyroom.auth.dto;

import java.time.OffsetDateTime;

/**
 * Login response: bearer token, its expiry (business zone offset) and the
 * current user profile. mustChangePassword forces the client into the
 * change-password flow before any other API is allowed.
 */
public record LoginResponse(String accessToken, OffsetDateTime expiresAt,
                            UserProfile user, boolean mustChangePassword) {
}
