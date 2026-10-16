package edu.campus.studyroom.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 座位停用/恢复。MAINTENANCE 时必须提供 orderPolicy；reason 选填（仅审计）。 */
public record SeatStatusRequest(
        @NotBlank String status,
        String orderPolicy,
        @Size(max = 500, message = "操作原因不能超过 500 字") String reason,
        @NotNull Long expectedVersion
) {
}
