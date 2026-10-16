package edu.campus.studyroom.resource.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import edu.campus.studyroom.resource.domain.RoomStatus;

import java.time.LocalTime;

/** 房间列表项（含匹配座位计数）。 */
public record RoomListItem(
        String id,
        String code,
        String name,
        String campus,
        String building,
        @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
        int rows,
        int cols,
        RoomStatus status,
        int matchingSeatCount
) {
}
