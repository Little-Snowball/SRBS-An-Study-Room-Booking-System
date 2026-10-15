package edu.campus.studyroom.unit;

import edu.campus.studyroom.reservation.domain.TimeRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T07/T11/T13 相关纯函数：区间重叠判定 [start, end)，相邻不冲突。
 */
class TimeRangeTest {

    private static final LocalDateTime T9 = LocalDateTime.of(2026, 10, 15, 9, 0);
    private static final LocalDateTime T10 = LocalDateTime.of(2026, 10, 15, 10, 0);
    private static final LocalDateTime T11 = LocalDateTime.of(2026, 10, 15, 11, 0);

    @Test
    @DisplayName("半开区间：相邻预约不冲突")
    void adjacentRangesDoNotOverlap() {
        TimeRange a = TimeRange.of(T9, T10);
        TimeRange b = TimeRange.of(T10, T11);
        assertFalse(a.overlaps(b));
        assertFalse(b.overlaps(a));
    }

    @Test
    @DisplayName("区间重叠：a.start < b.end 且 a.end > b.start")
    void overlappingRanges() {
        TimeRange a = TimeRange.of(T9, T11);
        TimeRange b = TimeRange.of(T10, LocalDateTime.of(2026, 10, 15, 12, 0));
        assertTrue(a.overlaps(b));
        assertTrue(b.overlaps(a));
    }

    @Test
    @DisplayName("包含与秒数")
    void containsAndSeconds() {
        TimeRange a = TimeRange.of(T9, T11);
        assertTrue(a.contains(T10));
        assertFalse(a.contains(T11)); // 右开
        assertEquals(7200, a.seconds());
    }

    @Test
    @DisplayName("start < end 不成立时拒绝")
    void rejectsInvalidRange() {
        assertThrows(IllegalArgumentException.class, () -> TimeRange.of(T10, T10));
        assertThrows(IllegalArgumentException.class, () -> TimeRange.of(T11, T10));
    }
}
