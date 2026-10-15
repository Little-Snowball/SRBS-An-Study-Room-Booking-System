package edu.campus.studyroom.user.dto;

/**
 * Create-student response: the new account view plus the one-time password.
 * The password exists only in this response; it is never logged or re-readable.
 */
public record CreateStudentResponse(
        AdminUserItem user,
        String oneTimePassword
) {
}
