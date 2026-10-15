package edu.campus.studyroom.user.dto;

/**
 * Result of a password reset. oneTimePassword is non-null only on the first
 * successful response; a same-key replay returns credentialAlreadyIssued=true
 * with a null password so the secret is never disclosed twice.
 */
public record OneTimePasswordResponse(
        String userId,
        String oneTimePassword,
        boolean credentialAlreadyIssued
) {
}
