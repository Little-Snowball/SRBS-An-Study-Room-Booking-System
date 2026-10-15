package edu.campus.studyroom.reservation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reservation list item (my reservations / admin query). IDs are strings;
 * date-times carry a +08:00 offset. account/userName are populated for admin
 * list views and stay null in a student's own list. allowedActions is a hint
 * computed from the order's rule snapshot.
 */
public record ReservationListItem(
        String id,
        String orderNo,
        String roomName,
        String seatCode,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String status,
        String terminationReason,
        List<String> allowedActions,
        String account,
        String userName
) {
}
