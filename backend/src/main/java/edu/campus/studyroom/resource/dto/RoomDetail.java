package edu.campus.studyroom.resource.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import edu.campus.studyroom.resource.domain.RoomStatus;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/** 房间详情（不返回现场码哈希）。座位统计与时间戳用于管理端展示。 */
public record RoomDetail(
        String id,
        String code,
        String name,
        String campus,
        String building,
        int floorNo,
        @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
        int rows,
        int cols,
        RoomStatus status,
        long totalSeats,
        long normalSeats,
        long maintenanceSeats,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
