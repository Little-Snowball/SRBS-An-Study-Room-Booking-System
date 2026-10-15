package edu.campus.studyroom.rule;

import edu.campus.studyroom.rule.domain.RuleConfig;

import java.time.ZoneId;

/**
 * 前端展示口径的生效规则，不泄露管理员/审计信息。
 */
public record PublicRuleView(
        int advanceDays,
        int slotMinutes,
        int minMinutes,
        int maxMinutes,
        int cancelBeforeMinutes,
        int checkinEarlyMinutes,
        int checkinLateMinutes,
        int leaveMaxMinutes,
        int leaveMaxCount,
        int noShowPenalty,
        int leaveTimeoutPenalty,
        int minCredit,
        int rewardStartMinutes,
        int rewardBase,
        int rewardStepMinutes,
        int rewardStepScore,
        int rewardCap
) {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static PublicRuleView from(RuleConfig config) {
        return new PublicRuleView(
                config.advanceDays(), config.slotMinutes(), config.minMinutes(), config.maxMinutes(),
                config.cancelBeforeMinutes(), config.checkinEarlyMinutes(), config.checkinLateMinutes(),
                config.leaveMaxMinutes(), config.leaveMaxCount(), config.noShowPenalty(),
                config.leaveTimeoutPenalty(), config.minCredit(), config.rewardStartMinutes(),
                config.rewardBase(), config.rewardStepMinutes(), config.rewardStepScore(),
                config.rewardCap());
    }
}
