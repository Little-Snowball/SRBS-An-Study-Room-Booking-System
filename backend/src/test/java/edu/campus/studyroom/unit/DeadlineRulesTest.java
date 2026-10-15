package edu.campus.studyroom.unit;

import edu.campus.studyroom.reservation.domain.DeadlineRules;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.rule.domain.RuleConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T16/T17/T22/T23：期限推导。签到截止 min(start+late, end)；违约用 now > due；end 用 now >= end。
 */
class DeadlineRulesTest {

    private final RuleConfig rule = RuleConfig.defaults();
    private final LocalDateTime start = LocalDateTime.of(2026, 10, 15, 9, 0);
    private final LocalDateTime end = LocalDateTime.of(2026, 10, 15, 11, 0);

    @Test
    @DisplayName("签到截止 = min(start+15min, end)")
    void checkinDue() {
        assertEquals(LocalDateTime.of(2026, 10, 15, 9, 15), DeadlineRules.checkinDue(start, rule, end));
        // 最短预约 30 分钟 + 宽限 15：截止提前到 end
        LocalDateTime shortEnd = LocalDateTime.of(2026, 10, 15, 9, 10);
        assertEquals(shortEnd, DeadlineRules.checkinDue(start, rule, shortEnd));
    }

    @Test
    @DisplayName("取消截止 = start - 15min；签到窗口起点 = start - 15min")
    void cancelAndCheckinOpen() {
        assertEquals(LocalDateTime.of(2026, 10, 15, 8, 45), DeadlineRules.cancelDue(start, rule));
        assertEquals(LocalDateTime.of(2026, 10, 15, 8, 45), DeadlineRules.checkinOpensAt(start, rule));
    }

    @Test
    @DisplayName("暂离截止 = last_leave + 30min")
    void leaveDue() {
        assertEquals(LocalDateTime.of(2026, 10, 15, 10, 30),
                DeadlineRules.leaveDueAt(LocalDateTime.of(2026, 10, 15, 10, 0), rule));
    }

    @Test
    @DisplayName("爽约：now > checkinDue 且截止早于 end")
    void noShow() {
        LocalDateTime checkinDue = DeadlineRules.checkinDue(start, rule, end);
        assertFalse(DeadlineRules.noShowDue(LocalDateTime.of(2026, 10, 15, 9, 15), checkinDue, end)); // 恰好不违约
        assertTrue(DeadlineRules.noShowDue(LocalDateTime.of(2026, 10, 15, 9, 15, 0, 1), checkinDue, end));
        // 截止等于 end 时不构成爽约（由 end 自动完成接管）
        assertFalse(DeadlineRules.noShowDue(LocalDateTime.of(2026, 10, 15, 11, 1), end, end));
    }

    @Test
    @DisplayName("暂离超时：now > leaveDue 且截止早于 end；等于/晚于 end 由自动完成接管")
    void leaveTimeout() {
        LocalDateTime leaveDue = LocalDateTime.of(2026, 10, 15, 10, 30);
        assertFalse(DeadlineRules.leaveTimeoutDue(LocalDateTime.of(2026, 10, 15, 10, 30), leaveDue, end));
        assertTrue(DeadlineRules.leaveTimeoutDue(LocalDateTime.of(2026, 10, 15, 10, 31), leaveDue, end));
        assertFalse(DeadlineRules.leaveTimeoutDue(LocalDateTime.of(2026, 10, 15, 11, 1), leaveDue, leaveDue));
    }

    @Test
    @DisplayName("到期：now >= end")
    void endReached() {
        assertFalse(DeadlineRules.endReached(LocalDateTime.of(2026, 10, 15, 10, 59), end));
        assertTrue(DeadlineRules.endReached(LocalDateTime.of(2026, 10, 15, 11, 0), end));
    }

    @Test
    @DisplayName("最早适用截止：RESERVED 用签到截止，LEFT_TEMP 用暂离截止，IN_USE 无")
    void earliestDue() {
        LocalDateTime checkinDue = DeadlineRules.checkinDue(start, rule, end);
        LocalDateTime leaveDue = LocalDateTime.of(2026, 10, 15, 10, 30);
        assertEquals(checkinDue, DeadlineRules.earliestDue(ReservationStatus.RESERVED, checkinDue, leaveDue, end));
        assertEquals(leaveDue, DeadlineRules.earliestDue(ReservationStatus.LEFT_TEMP, checkinDue, leaveDue, end));
        assertNull(DeadlineRules.earliestDue(ReservationStatus.IN_USE, checkinDue, leaveDue, end));
    }
}
