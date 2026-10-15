package edu.campus.studyroom.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Change-password request body. The new password must satisfy
 * {@code PasswordPolicy} (12-64 chars, no account/name fragments).
 */
public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空") String oldPassword,
        @NotBlank(message = "新密码不能为空") String newPassword) {
}
