package edu.campus.studyroom.resource.dto;

import edu.campus.studyroom.resource.domain.SeatStatus;

/** 座位状态变更影响预览（预估数量，不是执行依据）。 */
public record SeatImpact(
        String seatId,
        String seatCode,
        SeatStatus currentStatus,
        int reservedCount,
        int inUseCount,
        int leftTempCount,
        int totalAffected
) {
}
