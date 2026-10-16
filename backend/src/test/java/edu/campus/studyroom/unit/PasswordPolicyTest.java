package edu.campus.studyroom.unit;

import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.security.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密码 6—12 位、非空白即可（不含学号/姓名等额外限制）；BCrypt cost=12。
 */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    @DisplayName("合法密码通过")
    void acceptsValid() {
        assertDoesNotThrow(() -> policy.validate("stu123", "20240001", "张予安"));
    }

    @Test
    @DisplayName("长度边界：5 位拒绝，6/12 位通过，13 位拒绝")
    void lengthBounds() {
        assertThrows(BusinessException.class, () -> policy.validate("Ab1cd", "20240001", "张予安"));
        assertDoesNotThrow(() -> policy.validate("Ab1cde", "20240001", "张予安"));
        assertDoesNotThrow(() -> policy.validate("A1bcdefghij", "20240001", "张予安"));
        assertThrows(BusinessException.class, () -> policy.validate("A1bcdefghijkl", "20240001", "张予安"));
    }

    @Test
    @DisplayName("包含学号或姓名不再拒绝")
    void containsAccountOrNameAllowed() {
        assertDoesNotThrow(() -> policy.validate("My-20240001", "20240001", "张予安"));
        assertDoesNotThrow(() -> policy.validate("zhangyu'an", "20240009", "Zhangyu'an"));
    }

    @Test
    @DisplayName("全空白密码拒绝")
    void blankRejected() {
        assertThrows(BusinessException.class, () -> policy.validate("            ", "20240001", "张予安"));
    }

    @Test
    @DisplayName("BCrypt 哈希与校验")
    void hashAndMatch() {
        String hash = policy.hash("stu123");
        assertNotEquals("stu123", hash);
        assertTrue(policy.matches("stu123", hash));
        assertFalse(policy.matches("wrongpw", hash));
    }
}
