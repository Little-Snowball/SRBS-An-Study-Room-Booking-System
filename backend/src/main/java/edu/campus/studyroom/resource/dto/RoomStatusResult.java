package edu.campus.studyroom.resource.dto;

import edu.campus.studyroom.resource.domain.RoomStatus;

/**
 * Outcome of a room open/close change: how many effective orders were
 * cancelled (no fault), kept active under a retained-service permission, or
 * force-ended (zero reward/penalty).
 */
public record RoomStatusResult(
        String roomId,
        RoomStatus status,
        int affectedCancelled,
        int affectedKept,
        int affectedEnded
) {
}
