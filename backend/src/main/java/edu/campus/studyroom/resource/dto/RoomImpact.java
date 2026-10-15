package edu.campus.studyroom.resource.dto;

import edu.campus.studyroom.resource.domain.RoomStatus;

import java.time.LocalTime;

/**
 * 房间状态/营业时间变更影响预览（预估数量，不是执行依据）。
 * currentStatus/targetStatus 与前后营业时间一并回报，便于前端二次确认。
 */
public record RoomImpact(
        String roomId,
        RoomStatus currentStatus,
        RoomStatus targetStatus,
        LocalTime newOpenTime,
        LocalTime newCloseTime,
        int reservedCount,
        int inUseCount,
        int leftTempCount,
        int totalAffected,
        String note
) {
}
