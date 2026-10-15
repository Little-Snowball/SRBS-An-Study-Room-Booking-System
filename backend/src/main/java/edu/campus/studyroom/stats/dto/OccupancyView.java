package edu.campus.studyroom.stats.dto;

import java.time.OffsetDateTime;

/**
 * Occupancy snapshot: seats actually IN_USE right now over the servable seat
 * set of OPEN rooms. LEFT_TEMP never counts; KEEP_ACTIVE retained occupancy is
 * reported separately and never enters numerator or denominator.
 */
public record OccupancyView(
        long numerator,
        long denominator,
        Double rate,
        long keptActiveSeats,
        OffsetDateTime asOfAt
) {
}
