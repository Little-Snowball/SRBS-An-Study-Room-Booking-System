package edu.campus.studyroom.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Check-in request. The on-site code is exactly six digits and MAY carry leading
 * zeros, so it travels as a string; BCrypt verification happens outside the
 * command transaction against the room's checkin_code_hash.
 */
public record CheckInRequest(
        @NotNull(message = "现场码不能为空")
        @Pattern(regexp = "^[0-9]{6}$", message = "现场码须为 6 位数字")
        String code
) {
}
