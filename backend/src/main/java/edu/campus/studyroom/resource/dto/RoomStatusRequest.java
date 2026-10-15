package edu.campus.studyroom.resource.dto;

import edu.campus.studyroom.resource.domain.OrderPolicy;
import edu.campus.studyroom.resource.domain.RoomStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 闭馆/开放请求。orderPolicy 必填：对已有有效订单的处理策略。 */
public record RoomStatusRequest(
        @NotNull RoomStatus status,
        @NotNull OrderPolicy orderPolicy,
        @NotBlank String reason,
        @NotNull Long expectedVersion
) {
}
