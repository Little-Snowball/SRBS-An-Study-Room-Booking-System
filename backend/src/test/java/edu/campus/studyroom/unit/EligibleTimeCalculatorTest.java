package edu.campus.studyroom.unit;

import edu.campus.studyroom.reservation.domain.AttendanceEvent;
import edu.campus.studyroom.reservation.domain.AttendanceEventType;
import edu.campus.studyroom.settlement.domain.EligibleTimeCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T25/T26/T30：有效在座时长。提前签到不计、暂离扣除、累计秒再取整；
 * 事件缺失/重复必须抛一致性异常，禁止猜测时长。
 */
class EligibleTimeCalculatorTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 15, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 15, 13, 0);

    private static AttendanceEvent event(AttendanceEventType type, LocalDateTime at) {
        AttendanceEvent e = new AttendanceEvent();
        e.setEventType(type);
        e.setEventAt(at);
        return e;
    }

    private static long minutes(long seconds) {
        return seconds / 60;
    }

    @Test
    @DisplayName("119 分钟在座 → 119*60 秒（奖励 0）")
    void fullUse119() {
        List<AttendanceEvent> events = List.of(
                event(AttendanceEventType.CHECK_IN, START),
                event(AttendanceEventType.CHECK_OUT, START.plusMinutes(119)));
        assertEquals(119 * 60, EligibleTimeCalculator.eligibleSeconds(events, START, END));
    }

    @Test
    @DisplayName("暂离扣除：在座 120 分钟中含 30 分钟暂离 → 有效 90 分钟")
    void leaveDeducted() {
        List<AttendanceEvent> events = List.of(
                event(AttendanceEventType.CHECK_IN, START),
                event(AttendanceEventType.LEAVE, START.plusMinutes(60)),
                event(AttendanceEventType.RETURN, START.plusMinutes(90)),
                event(AttendanceEventType.CHECK_OUT, START.plusMinutes(150)));
        // 在座段 60 + 60 = 120 分钟，其中暂离 30 分钟不计（有效在座 120 分钟）
        assertEquals(120 * 60, EligibleTimeCalculator.eligibleSeconds(events, START, END));
    }

    @Test
    @DisplayName("暂离 30 分钟后不再回座：有效在座仅 60 分钟")
    void leaveThenCheckout() {
        List<AttendanceEvent> events = List.of(
                event(AttendanceEventType.CHECK_IN, START),
                event(AttendanceEventType.LEAVE, START.plusMinutes(60)),
                event(AttendanceEventType.CHECK_OUT, START.plusMinutes(120)));
        // 在座段仅 9:00—10:00，之后未回座
        assertEquals(60 * 60, EligibleTimeCalculator.eligibleSeconds(events, START, END));
    }

    @Test
    @DisplayName("提前签到不计 start 之前；超出 end 不计")
    void earlyAndOverrunClipped() {
        List<AttendanceEvent> events = List.of(
                event(AttendanceEventType.CHECK_IN, START.minusMinutes(20)), // 提前 20 分钟
                event(AttendanceEventType.CHECK_OUT, END.plusMinutes(30)));  // 超 30 分钟
        assertEquals(240 * 60, EligibleTimeCalculator.eligibleSeconds(events, START, END));
    }

    @Test
    @DisplayName("未签到的爽约单无在座段")
    void noShowHasNoSeatTime() {
        List<AttendanceEvent> events = List.of(
                event(AttendanceEventType.VIOLATION_END, START.plusMinutes(15)));
        assertEquals(0, EligibleTimeCalculator.eligibleSeconds(events, START, END));
    }

    @Test
    @DisplayName("宽限模式：终止事件未落库时按截止时刻闭合")
    void closeOpenSegment() {
        List<AttendanceEvent> events = List.of(
                event(AttendanceEventType.CHECK_IN, START.plusMinutes(30)));
        long seconds = EligibleTimeCalculator.eligibleSeconds(events, START, END, START.plusMinutes(120));
        assertEquals(90 * 60, seconds);
    }

    @Test
    @DisplayName("暂离中签退：未闭合暂离段截止到签退时刻")
    void checkoutWhileLeftTemp() {
        List<AttendanceEvent> events = List.of(
                event(AttendanceEventType.CHECK_IN, START),
                event(AttendanceEventType.LEAVE, START.plusMinutes(60)));
        long seconds = EligibleTimeCalculator.eligibleSeconds(events, START, END, START.plusMinutes(100));
        assertEquals(60 * 60, seconds);
    }

    @Test
    @DisplayName("重复进入事件抛一致性异常")
    void duplicateEnterThrows() {
        List<AttendanceEvent> events = List.of(
                event(AttendanceEventType.CHECK_IN, START),
                event(AttendanceEventType.CHECK_IN, START.plusMinutes(10)),
                event(AttendanceEventType.CHECK_OUT, START.plusMinutes(120)));
        assertThrows(RuntimeException.class,
                () -> EligibleTimeCalculator.eligibleSeconds(events, START, END));
    }

    @Test
    @DisplayName("缺失进入事件的 LEAVE 抛一致性异常")
    void orphanLeaveThrows() {
        List<AttendanceEvent> events = List.of(
                event(AttendanceEventType.LEAVE, START.plusMinutes(10)),
                event(AttendanceEventType.CHECK_OUT, START.plusMinutes(120)));
        assertThrows(RuntimeException.class,
                () -> EligibleTimeCalculator.eligibleSeconds(events, START, END));
    }

    @Test
    @DisplayName("严格模式下未闭合在座段抛异常")
    void unclosedStrictThrows() {
        List<AttendanceEvent> events = List.of(
                event(AttendanceEventType.CHECK_IN, START));
        assertThrows(RuntimeException.class,
                () -> EligibleTimeCalculator.eligibleSeconds(events, START, END));
    }

    @Test
    @DisplayName("累计秒数最后取整：150 分钟含两次暂离")
    void multipleLeaves() {
        List<AttendanceEvent> events = List.of(
                event(AttendanceEventType.CHECK_IN, START),
                event(AttendanceEventType.LEAVE, START.plusMinutes(30)),
                event(AttendanceEventType.RETURN, START.plusMinutes(45)),
                event(AttendanceEventType.LEAVE, START.plusMinutes(90)),
                event(AttendanceEventType.RETURN, START.plusMinutes(105)),
                event(AttendanceEventType.CHECK_OUT, START.plusMinutes(150)));
        // 30 + 45 + 45 = 120 分钟
        assertEquals(120 * 60, EligibleTimeCalculator.eligibleSeconds(events, START, END));
        assertEquals(120, minutes(EligibleTimeCalculator.eligibleSeconds(events, START, END)));
    }
}
