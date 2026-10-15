package edu.campus.studyroom.unit;

import edu.campus.studyroom.reservation.domain.TimeGrid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T05/T06：日期范围与格点规则。起止均为整点或半点，恰好格点不额外进一格。
 */
class TimeGridTest {

    @Test
    @DisplayName("整点与半点在格点上，10:15 不在")
    void onGridCheck() {
        assertTrue(TimeGrid.isOnGrid(LocalDateTime.of(2026, 10, 15, 10, 0), 30));
        assertTrue(TimeGrid.isOnGrid(LocalDateTime.of(2026, 10, 15, 10, 30), 30));
        assertFalse(TimeGrid.isOnGrid(LocalDateTime.of(2026, 10, 15, 10, 15), 30));
        assertFalse(TimeGrid.isOnGrid(LocalDateTime.of(2026, 10, 15, 10, 0, 30), 30));
    }

    @Test
    @DisplayName("ceil：恰好格点不前移，10:07 → 10:30，10:01 → 10:30")
    void ceilToGrid() {
        assertEquals(LocalDateTime.of(2026, 10, 15, 10, 0),
                TimeGrid.ceil(LocalDateTime.of(2026, 10, 15, 10, 0), 30));
        assertEquals(LocalDateTime.of(2026, 10, 15, 10, 30),
                TimeGrid.ceil(LocalDateTime.of(2026, 10, 15, 10, 7), 30));
        assertEquals(LocalDateTime.of(2026, 10, 15, 10, 30),
                TimeGrid.ceil(LocalDateTime.of(2026, 10, 15, 10, 1), 30));
    }

    @Test
    @DisplayName("当天最早起点：max(now, openTime) 向上对齐，恰好格点不进格")
    void earliestStart() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        // now=10:07, open=07:00 -> 10:30
        assertEquals(LocalDateTime.of(2026, 10, 15, 10, 30),
                TimeGrid.earliestStart(LocalDateTime.of(2026, 10, 15, 10, 7), LocalTime.of(7, 0), 30));
        // now=06:00, open=07:00 -> 07:00
        assertEquals(LocalDateTime.of(2026, 10, 15, 7, 0),
                TimeGrid.earliestStart(LocalDateTime.of(2026, 10, 15, 6, 0), LocalTime.of(7, 0), 30));
        // now=09:00 exactly -> 09:00
        assertEquals(LocalDateTime.of(2026, 10, 15, 9, 0),
                TimeGrid.earliestStart(LocalDateTime.of(2026, 10, 15, 9, 0), LocalTime.of(7, 0), 30));
        // now=10:07, open=10:00 -> 10:30（营业时间未到也按 now 取大者）
        assertEquals(LocalDateTime.of(2026, 10, 15, 10, 30),
                TimeGrid.earliestStart(LocalDateTime.of(2026, 10, 15, 10, 7), LocalTime.of(10, 0), 30));
        assertEquals(date, LocalDate.of(2026, 10, 15));
    }

    @Test
    @DisplayName("latestEnd 取当天关门时间")
    void latestEnd() {
        assertEquals(LocalDateTime.of(2026, 10, 15, 22, 30),
                TimeGrid.latestEnd(LocalDate.of(2026, 10, 15), LocalTime.of(22, 30)));
    }
}
