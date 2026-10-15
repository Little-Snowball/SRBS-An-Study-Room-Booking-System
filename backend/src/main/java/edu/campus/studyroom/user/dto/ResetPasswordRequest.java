package edu.campus.studyroom.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin resets a student password. A fresh random one-time password is generated,
 * mustChangePassword is forced and all sessions are revoked. The plaintext is
 * returned exactly once; a same-key replay only reports that the credential was
 * already issued.
 */
public record ResetPasswordRequest(
        @NotBlank @Size(min = 5, max = 500, message = "操作原因须为 5—500 字") String reason
) {
}
