package edu.campus.studyroom.resource.dto;

import jakarta.validation.constraints.NotNull;

/** 座位标签编辑（有历史订单时禁止改坐标/编码，故仅标签可改）。 */
public record UpdateSeatRequest(
        Boolean hasPower,
        Boolean nearWindow,
        Boolean quietZone,
        @NotNull Long expectedVersion
) {
}
