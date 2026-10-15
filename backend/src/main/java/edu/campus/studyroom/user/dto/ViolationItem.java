package edu.campus.studyroom.user.dto;

import java.time.OffsetDateTime;

/**
 * 学生本人的处罚记录。appealable 表示当前是否仍在申诉窗口内且无待审工单，
 * 该标记仅为提示，提交申诉时服务端会重新判定窗口与唯一性。
 */
public record ViolationItem(
        String id,
        String reservationId,
        String reason,
        String status,
        OffsetDateTime eventAt,
        OffsetDateTime notifiedAt,
        int deducted,
        boolean appealable,
        String orderNo,
        String seatCode,
        String roomName
) {
}