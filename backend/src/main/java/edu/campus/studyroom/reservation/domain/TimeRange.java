package edu.campus.studyroom.reservation.domain;

import java.time.LocalDateTime;

/**
 * Half-open time range [start, end). Two ranges overlap iff
 * a.start &lt; b.end &amp;&amp; a.end &gt; b.start; adjacent ranges never conflict.
 */
public record TimeRange(LocalDateTime start, LocalDateTime end) {

    public TimeRange {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("time range must satisfy start < end");
        }
    }

    public boolean overlaps(TimeRange other) {
        return start.isBefore(other.end) && end.isAfter(other.start);
    }

    public boolean contains(LocalDateTime instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    public long seconds() {
        return java.time.Duration.between(start, end).toSeconds();
    }

    public static TimeRange of(LocalDateTime start, LocalDateTime end) {
        return new TimeRange(start, end);
    }
}
