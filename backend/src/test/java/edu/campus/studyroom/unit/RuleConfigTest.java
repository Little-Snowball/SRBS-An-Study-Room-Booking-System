package edu.campus.studyroom.unit;

import edu.campus.studyroom.rule.domain.RuleConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T25/T27/T48：奖励公式与封顶。默认常量不得写死，全部读规则。
 */
class RuleConfigTest {

    @Test
    @DisplayName("默认值反序列化与 JSON 往返")
    void defaultsRoundTrip() {
        RuleConfig config = RuleConfig.defaults();
        assertEquals(120, config.rewardStartMinutes());
        assertEquals(5, config.rewardCap());
        RuleConfig parsed = RuleConfig.fromJson(config.toJson());
        assertEquals(config, parsed);
    }

    @Test
    @DisplayName("奖励：119/120/149/150/210 分钟 → 0/2/2/3/5")
    void rewardForSeconds() {
        RuleConfig config = RuleConfig.defaults();
        assertEquals(0, config.rewardForSeconds(119 * 60));
        assertEquals(2, config.rewardForSeconds(120 * 60));
        assertEquals(2, config.rewardForSeconds(149 * 60));
        assertEquals(3, config.rewardForSeconds(150 * 60));
        assertEquals(5, config.rewardForSeconds(210 * 60));
        assertEquals(5, config.rewardForSeconds(10 * 3600)); // 封顶
    }

    @Test
    @DisplayName("非默认规则：起点 60、步长 15、stepScore 2、cap 4")
    void nonDefaultRule() {
        RuleConfig config = new RuleConfig(1, 2, 30, 30, 240, 15, 15, 15, 30, 2,
                7, 7, 80, 100, 60, 1, 15, 2, 4, "AUTO_COMPLETE_NO_REWARD");
        assertNull(config.validationError());
        assertEquals(0, config.rewardForSeconds(59 * 60));
        assertEquals(1, config.rewardForSeconds(60 * 60));
        assertEquals(3, config.rewardForSeconds(75 * 60));  // 1 + 2*1
        assertEquals(4, config.rewardForSeconds(200 * 60)); // 封顶 4
        assertEquals(80, config.minCredit());
    }

    @Test
    @DisplayName("字段范围校验：固定策略不可改、late 必须小于 min")
    void validation() {
        assertNull(RuleConfig.defaults().validationError());

        RuleConfig badSlot = new RuleConfig(1, 2, 60, 30, 240, 15, 15, 15, 30, 2,
                10, 10, 60, 100, 120, 2, 30, 1, 5, "AUTO_COMPLETE_NO_REWARD");
        assertNotNull(badSlot.validationError());

        RuleConfig lateTooBig = new RuleConfig(1, 2, 30, 30, 240, 15, 15, 30, 30, 2,
                10, 10, 60, 100, 120, 2, 30, 1, 5, "AUTO_COMPLETE_NO_REWARD");
        assertNotNull(lateTooBig.validationError());

        RuleConfig advanceTooBig = RuleConfig.fromJson(RuleConfig.defaults().toJson());
        // advanceDays=31 超出 0—30
        RuleConfig bad = new RuleConfig(1, 31, 30, 30, 240, 15, 15, 15, 30, 2,
                10, 10, 60, 100, 120, 2, 30, 1, 5, "AUTO_COMPLETE_NO_REWARD");
        assertNotNull(bad.validationError());

        RuleConfig maxTooBig = new RuleConfig(1, 2, 30, 30, 721, 15, 15, 15, 30, 2,
                10, 10, 60, 100, 120, 2, 30, 1, 5, "AUTO_COMPLETE_NO_REWARD");
        assertNotNull(maxTooBig.validationError());

        assertNotNull(Map.of());
    }
}
