package edu.campus.studyroom.resource.dto;

import edu.campus.studyroom.resource.domain.SeatStatus;

/**
 * Outcome of a seat status change: how many effective orders were cancelled
 * (no fault), kept active under a retained-service permission, or force-ended.
 */
public record SeatStatusResult(
        String seatId,
        SeatStatus status,
        int affectedCancelled,
        int affectedKept,
        int affectedEnded
) {
}
