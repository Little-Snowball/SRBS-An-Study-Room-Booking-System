package edu.campus.studyroom.settlement.domain;

import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.reservation.domain.AttendanceEvent;
import edu.campus.studyroom.reservation.domain.AttendanceEventType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 有效在座时长纯函数（手册 8.5/9：签退从事件计算在座时长，禁止用猜测时长发奖励）。
 * <p>不依赖 Spring，可单独单测。输入按 (event_at, id) 有序的考勤事件列表与订单
 * 半开区间 [start, end)，输出实际 IN_USE 段裁剪到该区间后的秒数之和：
 * <ul>
 *   <li>CHECK_IN / RETURN 进入在座；已在座时再次进入视为重复事件，抛一致性异常；</li>
 *   <li>LEAVE 结束在座段；不在座时出现 LEAVE 视为进入事件缺失，抛一致性异常；</li>
 *   <li>CHECK_OUT / AUTO_END / FORCE_END / VIOLATION_END / CANCEL 为终止事件：
 *       未闭合在座段在终止事件时刻闭合（VIOLATION_END 业务上最后 LEAVE 已闭合在座段；
 *       NO_SHOW 从无在座段），终止事件之后的事件不再参与计算；</li>
 *   <li>段裁剪到 [start, end)：提前签到（start 之前）不计，超出 end 的部分不计；</li>
 *   <li>事件缺失或重复一律抛 {@link BusinessException}（INTERNAL_ERROR），禁止猜测时长。</li>
 * </ul>
 * 单测预期（默认规则 rewardStartMinutes=120, rewardBase=2, rewardStepMinutes=30,
 * rewardStepScore=1, rewardCap=5，即 RuleConfig#rewardForSeconds）：
 * <table border="1">
 *   <caption>有效在座分钟数与奖励分值</caption>
 *   <tr><th>有效在座</th><th>119 分钟</th><th>120 分钟</th><th>149 分钟</th><th>150 分钟</th><th>210 分钟</th></tr>
 *   <tr><td>奖励分值</td><td>0</td><td>2</td><td>2</td><td>3</td><td>5</td></tr>
 * </table>
 * 说明：119 &lt; 120 起点为 0；120 = base 2；149 未跨过一个 30 分钟步长仍为 2；
 * 150 跨一个步长为 3；210 为 2+3=5（未超 cap 5）。
 */
public final class EligibleTimeCalculator {

    private EligibleTimeCalculator() {
    }

    /**
     * 严格模式：在座段必须由 LEAVE 或终止事件闭合，未闭合即抛一致性异常。
     * 适用于结算时考勤事件已完整落库的场景（如 LEAVE_TIMEOUT、CANCEL、终态重放）。
     */
    public static long eligibleSeconds(List<AttendanceEvent> events, LocalDateTime start, LocalDateTime end) {
        return eligibleSeconds(events, start, end, null);
    }

    /**
     * 宽限模式：把未闭合的在座段在 {@code closeOpenSegmentAt} 时刻闭合后再累计。
     * 适用于终止事件尚未写入、但业务上即视为在该时刻终止的场景：
     * 主动签退（CHECK_OUT 事件时刻=now）、到期自动完成（AUTO_END 事件时刻=end）、
     * 管理员强制结束（FORCE_END 事件时刻=now）。
     *
     * @param closeOpenSegmentAt 未闭合在座段的闭合时刻；传 null 等价于严格模式
     */
    public static long eligibleSeconds(List<AttendanceEvent> events, LocalDateTime start, LocalDateTime end,
                                       LocalDateTime closeOpenSegmentAt) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw BusinessException.internal("订单区间非法，无法计算有效在座时长");
        }
        if (events == null) {
            throw BusinessException.internal("考勤事件缺失，无法计算有效在座时长");
        }
        long total = 0L;
        LocalDateTime inSeatSince = null;
        for (AttendanceEvent event : events) {
            if (event == null || event.getEventType() == null || event.getEventAt() == null) {
                throw BusinessException.internal("考勤事件字段缺失，无法计算有效在座时长");
            }
            LocalDateTime at = event.getEventAt();
            switch (event.getEventType()) {
                case CHECK_IN, RETURN -> {
                    if (inSeatSince != null) {
                        throw BusinessException.internal("重复的进入在座事件（" + event.getEventType()
                                + "@" + at + "），在座段未闭合，考勤事件不一致");
                    }
                    inSeatSince = at;
                }
                case LEAVE -> {
                    if (inSeatSince == null) {
                        throw BusinessException.internal("缺失进入事件的 LEAVE@" + at + "，考勤事件不一致");
                    }
                    total += overlapSeconds(inSeatSince, at, start, end);
                    inSeatSince = null;
                }
                case CHECK_OUT, AUTO_END, FORCE_END, VIOLATION_END, CANCEL -> {
                    if (inSeatSince != null) {
                        total += overlapSeconds(inSeatSince, at, start, end);
                        inSeatSince = null;
                    }
                    // 终止事件之后订单已终态，不再有任何业务意义
                    return total;
                }
                default -> throw BusinessException.internal("未知考勤事件类型: " + event.getEventType());
            }
        }
        if (inSeatSince != null) {
            if (closeOpenSegmentAt == null) {
                throw BusinessException.internal("在座段自 " + inSeatSince + " 起未闭合，考勤事件缺失");
            }
            total += overlapSeconds(inSeatSince, closeOpenSegmentAt, start, end);
        }
        return total;
    }

    /** [from, to) 与 [start, end) 的交集秒数；交集为空返回 0。 */
    private static long overlapSeconds(LocalDateTime from, LocalDateTime to, LocalDateTime start,
                                       LocalDateTime end) {
        LocalDateTime fromInRange = from.isBefore(start) ? start : from;
        LocalDateTime toInRange = to.isAfter(end) ? end : to;
        if (!fromInRange.isBefore(toInRange)) {
            return 0L;
        }
        return Duration.between(fromInRange, toInRange).toSeconds();
    }
}
