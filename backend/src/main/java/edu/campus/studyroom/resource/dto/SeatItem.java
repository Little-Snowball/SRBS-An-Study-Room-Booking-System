package edu.campus.studyroom.resource.dto;

import edu.campus.studyroom.resource.domain.SeatStatus;

/** 座位项（管理端布局）。id/roomId 为字符串，含乐观锁版本。 */
public record SeatItem(
        String id,
        String roomId,
        String code,
        int rowNo,
        int colNo,
        boolean hasPower,
        boolean nearWindow,
        boolean quietZone,
        SeatStatus status,
        long version
) {
}
