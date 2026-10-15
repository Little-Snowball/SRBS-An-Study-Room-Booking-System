package edu.campus.studyroom.reservation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reservation detail view. IDs are strings; every date-time is an OffsetDateTime
 * serialized with a +08:00 offset. account/userName identify the owning student
 * (populated for admin views as well). allowedActions is a UI hint only — the
 * server re-validates every command.
 */
public record ReservationDetail(
        String id,
        String orderNo,
        String userId,
        String account,
        String userName,
        String roomId,
        String roomName,
        String seatId,
        String seatCode,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime checkInAt,
        OffsetDateTime checkinDueAt,
        OffsetDateTime cancelDueAt,
        OffsetDateTime leaveDueAt,
        OffsetDateTime finishedAt,
        String status,
        String terminationReason,
        int leaveCount,
        String version,
        List<String> allowedActions,
        String ruleSnapshotSummary
) {
}
