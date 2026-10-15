package edu.campus.studyroom.common;

import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Single time conversion entry. All business deadlines use this clock;
 * database NOW() is never mixed into a command decision.
 * Business zone is Asia/Shanghai; cross-API date-times carry a +08:00 offset.
 */
public class BusinessClock {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessClock(Clock clock, @Value("${app.business-zone:Asia/Shanghai}") String zone) {
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public BusinessClock(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public ZoneId zone() {
        return zone;
    }

    /** Server-side instant; the only trusted "now" for deadline decisions. */
    public Instant now() {
        return clock.instant();
    }

    /** Business-local date-time (Asia/Shanghai). */
    public LocalDateTime nowLocal() {
        return LocalDateTime.ofInstant(clock.instant(), zone);
    }

    public LocalDate today() {
        return nowLocal().toLocalDate();
    }

    public OffsetDateTime nowOffset() {
        return OffsetDateTime.ofInstant(clock.instant(), zone);
    }

    public OffsetDateTime toOffset(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.atZone(zone).toOffsetDateTime();
    }

    public LocalDateTime toLocal(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null
                : offsetDateTime.atZoneSameInstant(zone).toLocalDateTime();
    }

    public Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.atZone(zone).toInstant();
    }
}
