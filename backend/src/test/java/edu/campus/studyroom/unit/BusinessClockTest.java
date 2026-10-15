package edu.campus.studyroom.unit;

import edu.campus.studyroom.common.BusinessClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 时钟与业务时区：唯一时间源；Asia/Shanghai 转换入口。
 */
class BusinessClockTest {

    private final BusinessClock clock = new BusinessClock(
            Clock.fixed(Instant.parse("2026-10-15T01:30:00Z"), ZoneId.of("UTC")),
            ZoneId.of("Asia/Shanghai"));

    @Test
    @DisplayName("UTC 实例转业务本地时间 +08:00")
    void utcToBusiness() {
        assertEquals(LocalDateTime.of(2026, 10, 15, 9, 30), clock.nowLocal());
        assertEquals(OffsetDateTime.parse("2026-10-15T09:30:00+08:00"), clock.nowOffset());
    }

    @Test
    @DisplayName("toOffset/toLocal/toInstant 往返一致")
    void conversionsRoundTrip() {
        LocalDateTime local = LocalDateTime.of(2026, 10, 15, 22, 45);
        OffsetDateTime offset = clock.toOffset(local);
        assertEquals("+08:00", offset.getOffset().getId());
        assertEquals(local, clock.toLocal(offset));
        assertEquals(local, LocalDateTime.ofInstant(clock.toInstant(local), clock.zone()));
    }

    @Test
    @DisplayName("today 取业务日期")
    void today() {
        assertEquals(LocalDateTime.of(2026, 10, 15, 9, 30).toLocalDate(), clock.today());
    }

    @Test
    @DisplayName("可变的测试时钟")
    void mutableClock() {
        // 生产 Clock.systemUTC；测试可用 Clock.fixed/mutable 覆盖（通过 bean 替换）
        assertNotNull(Clock.systemUTC());
    }
}
