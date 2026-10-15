package edu.campus.studyroom.resource.dto;

import java.util.List;

/** 房间座位布局（管理端）。 */
public record SeatLayout(String roomId, List<SeatItem> seats) {
}
