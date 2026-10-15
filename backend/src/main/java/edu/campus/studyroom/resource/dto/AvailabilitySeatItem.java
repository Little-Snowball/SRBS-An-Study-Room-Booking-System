package edu.campus.studyroom.resource.dto;

/** 可用性查询返回的单个座位（派生状态）。 */
public record AvailabilitySeatItem(
        String seatId,
        String code,
        int rowNo,
        int colNo,
        boolean hasPower,
        boolean nearWindow,
        boolean quietZone,
        boolean available,
        String periodStatus,
        String currentStatus
) {
}
