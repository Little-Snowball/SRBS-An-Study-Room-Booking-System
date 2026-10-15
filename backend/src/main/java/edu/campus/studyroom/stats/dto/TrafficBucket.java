package edu.campus.studyroom.stats.dto;

import java.time.OffsetDateTime;

/**
 * One 30-minute traffic bucket; empty buckets report count 0.
 */
public record TrafficBucket(
        OffsetDateTime startAt,
        long count
) {
}
