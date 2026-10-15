package edu.campus.studyroom.stats.dto;

/**
 * Room usage row. usageSeconds is the overlap of real IN_USE segments with
 * servable seat time (retained intervals restore the denominator); serviceSeconds
 * is the servable seat time of the range. usageRate is null when the denominator
 * is zero.
 */
public record RoomUsageRow(
        Long roomId,
        String roomName,
        long usageSeconds,
        long serviceSeconds,
        Double usageRate
) {
}
