package edu.campus.studyroom.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Admin resets a student password. A fresh random one-time password is generated,
 * The reset revokes all sessions and bumps auth_version. The plaintext is
 * returned exactly once; a same-key replay only reports that the credential was
 * already issued. The reason is optional and only recorded for the audit trail.
 */
public record ResetPasswordRequest(
        @Size(max = 500, message = "操作原因不能超过 500 字") String reason
) {
}
