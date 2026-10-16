package edu.campus.studyroom.resource.dto;

import edu.campus.studyroom.resource.domain.OrderPolicy;
import edu.campus.studyroom.resource.domain.RoomStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 闭馆/开放请求。orderPolicy 必填：对已有有效订单的处理策略；reason 选填（仅审计）。 */
public record RoomStatusRequest(
        @NotNull RoomStatus status,
        @NotNull OrderPolicy orderPolicy,
        @Size(max = 500, message = "操作原因不能超过 500 字") String reason,
        @NotNull Long expectedVersion
) {
}
