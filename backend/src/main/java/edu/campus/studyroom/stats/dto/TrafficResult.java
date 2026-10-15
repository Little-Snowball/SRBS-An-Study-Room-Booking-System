package edu.campus.studyroom.stats.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Check-in traffic for one date: 48 fixed 30-minute buckets starting at
 * midnight. Early check-ins fall into their real event bucket.
 */
public record TrafficResult(
        OffsetDateTime date,
        Long roomId,
        int bucketCount,
        List<TrafficBucket> buckets
) {
}
