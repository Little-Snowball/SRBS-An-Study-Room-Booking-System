package edu.campus.studyroom.unit;

import edu.campus.studyroom.reservation.domain.ReservationStateMachine;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 手册第 9 章命令允许矩阵：终态不可复活；有效状态不得互转非法。
 */
class ReservationStateMachineTest {

    @Test
    @DisplayName("RESERVED 可签到/取消/违约/防御性自动完成")
    void reservedTransitions() {
        assertTrue(ReservationStateMachine.canTransition(ReservationStatus.RESERVED, ReservationStatus.IN_USE));
        assertTrue(ReservationStateMachine.canTransition(ReservationStatus.RESERVED, ReservationStatus.CANCELLED));
        assertTrue(ReservationStateMachine.canTransition(ReservationStatus.RESERVED, ReservationStatus.VIOLATED));
        assertTrue(ReservationStateMachine.canTransition(ReservationStatus.RESERVED, ReservationStatus.COMPLETED));
        assertFalse(ReservationStateMachine.canTransition(ReservationStatus.RESERVED, ReservationStatus.LEFT_TEMP));
    }

    @Test
    @DisplayName("IN_USE 可暂离/签退/违约；LEFT_TEMP 可回座/签退/违约")
    void inUseAndLeftTempTransitions() {
        assertTrue(ReservationStateMachine.canTransition(ReservationStatus.IN_USE, ReservationStatus.LEFT_TEMP));
        assertTrue(ReservationStateMachine.canTransition(ReservationStatus.IN_USE, ReservationStatus.COMPLETED));
        assertTrue(ReservationStateMachine.canTransition(ReservationStatus.IN_USE, ReservationStatus.VIOLATED));
        assertFalse(ReservationStateMachine.canTransition(ReservationStatus.IN_USE, ReservationStatus.RESERVED));

        assertTrue(ReservationStateMachine.canTransition(ReservationStatus.LEFT_TEMP, ReservationStatus.IN_USE));
        assertTrue(ReservationStateMachine.canTransition(ReservationStatus.LEFT_TEMP, ReservationStatus.COMPLETED));
        assertTrue(ReservationStateMachine.canTransition(ReservationStatus.LEFT_TEMP, ReservationStatus.VIOLATED));
        assertFalse(ReservationStateMachine.canTransition(ReservationStatus.LEFT_TEMP, ReservationStatus.CANCELLED));
    }

    @Test
    @DisplayName("终态不可恢复为有效状态")
    void terminalNeverRevives() {
        for (ReservationStatus terminal : new ReservationStatus[]{
                ReservationStatus.COMPLETED, ReservationStatus.CANCELLED, ReservationStatus.VIOLATED}) {
            for (ReservationStatus target : ReservationStatus.values()) {
                assertFalse(ReservationStateMachine.canTransition(terminal, target),
                        terminal + " -> " + target + " 应被拒绝");
            }
        }
    }

    @Test
    @DisplayName("有效/终态判定")
    void effectiveAndTerminal() {
        assertTrue(ReservationStateMachine.isEffective(ReservationStatus.RESERVED));
        assertTrue(ReservationStateMachine.isEffective(ReservationStatus.IN_USE));
        assertTrue(ReservationStateMachine.isEffective(ReservationStatus.LEFT_TEMP));
        assertFalse(ReservationStateMachine.isEffective(ReservationStatus.COMPLETED));
        assertTrue(ReservationStateMachine.isTerminal(ReservationStatus.CANCELLED));
        assertFalse(ReservationStateMachine.isTerminal(ReservationStatus.RESERVED));
    }
}
