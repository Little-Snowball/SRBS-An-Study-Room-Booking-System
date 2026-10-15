package edu.campus.studyroom.unit;

import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.security.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 手册 11.3：密码 12—64 位、不包含学号或姓名、不 trim；BCrypt cost=12。
 */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    @DisplayName("合法密码通过")
    void acceptsValid() {
        assertDoesNotThrow(() -> policy.validate("Campus-2026-Xy", "20230001", "张予安"));
    }

    @Test
    @DisplayName("长度边界：11 位拒绝，12/64 位通过")
    void lengthBounds() {
        assertThrows(BusinessException.class, () -> policy.validate("Abcdefgh1jk", "20230001", "张予安"));
        assertDoesNotThrow(() -> policy.validate("Abcdefgh1jkl", "20230001", "张予安"));
        assertDoesNotThrow(() -> policy.validate("A1" + "x".repeat(62), "20230001", "张予安"));
        assertThrows(BusinessException.class, () -> policy.validate("A1" + "x".repeat(63), "20230001", "张予安"));
    }

    @Test
    @DisplayName("包含学号或姓名拒绝（姓名按原样大小写不敏感匹配）")
    void containsAccountOrName() {
        BusinessException byAccount = assertThrows(BusinessException.class,
                () -> policy.validate("My-20230001-Pass", "20230001", "张予安"));
        assertEquals(ErrorCode.VALIDATION_ERROR, byAccount.code());
        // 英文姓名按原样匹配（策略不做拼音转换）
        BusinessException byName = assertThrows(BusinessException.class,
                () -> policy.validate("zhangyu'an-2026", "20230009", "Zhangyu'an"));
        assertEquals(ErrorCode.VALIDATION_ERROR, byName.code());
    }

    @Test
    @DisplayName("全空白密码拒绝")
    void blankRejected() {
        assertThrows(BusinessException.class, () -> policy.validate("            ", "20230001", "张予安"));
    }

    @Test
    @DisplayName("BCrypt 哈希与校验")
    void hashAndMatch() {
        String hash = policy.hash("Campus-2026-Xy");
        assertNotEquals("Campus-2026-Xy", hash);
        assertTrue(policy.matches("Campus-2026-Xy", hash));
        assertFalse(policy.matches("wrong-password-1", hash));
    }
}
