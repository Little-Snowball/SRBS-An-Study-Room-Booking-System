package edu.campus.studyroom.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * Create-reservation request. IDs travel as strings (JS-safe); date-times MUST carry
 * an explicit offset such as +08:00 (Jackson rejects naive input).
 */
public record CreateReservationRequest(
        @NotBlank(message = "座位不能为空") String seatId,
        @NotNull(message = "开始时间不能为空") OffsetDateTime startAt,
        @NotNull(message = "结束时间不能为空") OffsetDateTime endAt
) {
}
