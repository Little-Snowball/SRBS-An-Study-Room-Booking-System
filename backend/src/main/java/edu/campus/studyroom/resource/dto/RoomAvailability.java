package edu.campus.studyroom.resource.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 房间可用性：格点布局 + 座位派生状态。 */
public record RoomAvailability(
        String roomId,
        int rows,
        int cols,
        String startAt,
        String endAt,
        String serverTime,
        List<AvailabilitySeatItem> seats
) {
}
