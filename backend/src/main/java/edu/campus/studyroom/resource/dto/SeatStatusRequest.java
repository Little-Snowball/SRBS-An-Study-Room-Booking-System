package edu.campus.studyroom.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 座位停用/恢复。MAINTENANCE 时必须提供 orderPolicy。 */
public record SeatStatusRequest(
        @NotBlank String status,
        String orderPolicy,
        @NotBlank String reason,
        @NotNull Long expectedVersion
) {
}
