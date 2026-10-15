package edu.campus.studyroom.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login request body. The account is trimmed server-side; the raw password is
 * never trimmed, logged or persisted.
 */
public record LoginRequest(
        @NotBlank(message = "学号/工号不能为空") String account,
        @NotBlank(message = "密码不能为空") String password) {
}
