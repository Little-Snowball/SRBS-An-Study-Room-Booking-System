package edu.campus.studyroom.rule.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Active rule configuration snapshot (manual 7.4). Field ranges are validated on
 * publish; schemaVersion, slotMinutes, creditCap and endPolicy are fixed strategy
 * for this phase and not editable through the normal rule editor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleConfig(
        int schemaVersion,
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
        int creditCap,
        int rewardStartMinutes,
        int rewardBase,
        int rewardStepMinutes,
        int rewardStepScore,
        int rewardCap,
        String endPolicy) {

    public static final int FIXED_SCHEMA_VERSION = 1;
    public static final int FIXED_SLOT_MINUTES = 30;
    public static final int FIXED_CREDIT_CAP = 100;
    public static final String FIXED_END_POLICY = "AUTO_COMPLETE_NO_REWARD";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static RuleConfig defaults() {
        return new RuleConfig(1, 2, 30, 30, 240, 15, 15, 15, 30, 2,
                10, 10, 60, 100, 120, 2, 30, 1, 5, "AUTO_COMPLETE_NO_REWARD");
    }

    public static RuleConfig fromJson(String json) {
        try {
            return MAPPER.readValue(json, RuleConfig.class);
        } catch (IOException e) {
            throw new UncheckedIOException("rule config json is invalid", e);
        }
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Field ranges per manual 7.4; returns null when valid, otherwise the reason. */
    public String validationError() {
        if (schemaVersion != FIXED_SCHEMA_VERSION || slotMinutes != FIXED_SLOT_MINUTES
                || creditCap != FIXED_CREDIT_CAP || !FIXED_END_POLICY.equals(endPolicy)) {
            return "schemaVersion/slotMinutes/creditCap/endPolicy 为本期固定策略，不允许修改";
        }
        if (advanceDays < 0 || advanceDays > 30) {
            return "advanceDays 须在 0—30";
        }
        if (minMinutes % 30 != 0 || maxMinutes % 30 != 0) {
            return "min/maxMinutes 须为 30 的倍数";
        }
        if (minMinutes < 30 || minMinutes > maxMinutes || maxMinutes > 720) {
            return "时长须满足 30<=min<=max<=720";
        }
        if (checkinLateMinutes >= minMinutes) {
            return "checkinLateMinutes 必须小于 minMinutes，避免出现没有有效签到机会的新单规则";
        }
        if (!rangeOk(checkinEarlyMinutes) || !rangeOk(checkinLateMinutes)
                || !rangeOk(cancelBeforeMinutes)) {
            return "签到/取消窗口分钟数须在 0—60";
        }
        if (leaveMaxMinutes < 1 || leaveMaxMinutes > 120) {
            return "leaveMaxMinutes 须在 1—120";
        }
        if (leaveMaxCount < 0 || leaveMaxCount > 10) {
            return "leaveMaxCount 须在 0—10";
        }
        if (noShowPenalty < 0 || noShowPenalty > 100
                || leaveTimeoutPenalty < 0 || leaveTimeoutPenalty > 100) {
            return "扣分分值须在 0—100";
        }
        if (minCredit < 0 || minCredit > 100) {
            return "minCredit 须在 0—100";
        }
        if (rewardStartMinutes <= 0 || rewardStepMinutes <= 0) {
            return "奖励起点与步长必须为正";
        }
        if (rewardBase < 0 || rewardBase > 100 || rewardStepScore < 0 || rewardStepScore > 100
                || rewardCap < 0 || rewardCap > 100) {
            return "奖励分值须在 0—100";
        }
        return null;
    }

    private static boolean rangeOk(int value) {
        return value >= 0 && value <= 60;
    }

    /**
     * Reward for eligible in-seat seconds:
     * 0 below rewardStartMinutes; otherwise min(rewardCap, rewardBase +
     * floor((m - rewardStartMinutes) / rewardStepMinutes) * rewardStepScore).
     * The default constants are never hard-coded by callers.
     */
    public int rewardForSeconds(long eligibleSeconds) {
        long minutes = eligibleSeconds / 60;
        if (minutes < rewardStartMinutes) {
            return 0;
        }
        long steps = (minutes - rewardStartMinutes) / rewardStepMinutes;
        long reward = rewardBase + steps * rewardStepScore;
        return (int) Math.min(rewardCap, Math.max(0, reward));
    }
}
