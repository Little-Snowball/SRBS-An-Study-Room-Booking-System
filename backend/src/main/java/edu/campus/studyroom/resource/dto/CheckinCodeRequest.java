package edu.campus.studyroom.resource.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 重设现场码。响应不含 code/hash；旧码立即失效。 */
public record CheckinCodeRequest(
        @Pattern(regexp = "^[0-9]{6}$") String newCode,
        @NotNull Long expectedVersion
) {
}
