package edu.campus.studyroom.settlement.service;

import edu.campus.studyroom.audit.AuditService;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.credit.CreditCommand;
import edu.campus.studyroom.credit.CreditResult;
import edu.campus.studyroom.credit.CreditService;
import edu.campus.studyroom.credit.domain.CreditLogType;
import edu.campus.studyroom.notification.NotificationService;
import edu.campus.studyroom.reservation.domain.AttendanceEvent;
import edu.campus.studyroom.reservation.domain.AttendanceEventType;
import edu.campus.studyroom.reservation.domain.DeadlineRules;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.domain.TerminationReason;
import edu.campus.studyroom.reservation.mapper.AttendanceEventMapper;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.rule.domain.RuleConfig;
import edu.campus.studyroom.settlement.SettlementOutcome;
import edu.campus.studyroom.settlement.SettlementService;
import edu.campus.studyroom.settlement.domain.EligibleTimeCalculator;
import edu.campus.studyroom.settlement.domain.SettlementResult;
import edu.campus.studyroom.settlement.domain.Violation;
import edu.campus.studyroom.settlement.domain.ViolationReason;
import edu.campus.studyroom.settlement.domain.ViolationStatus;
import edu.campus.studyroom.settlement.mapper.SettlementResultMapper;
import edu.campus.studyroom.settlement.mapper.ViolationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 终态结算（手册 8.5/9）。一个订单最多一个终态结算事件；同一业务事件只产生一次
 * 信用流水（event_key 唯一）。所有终态写（考勤事件/状态/流水/通知/审计）同事务。
 * <p>事务约定：
 * <ul>
 *   <li>settleOne 委托独立 bean {@link SettlementTransactionRunner}（避免 self-invocation
 *       丢失事务），REQUIRED 传播：调度器调用时是独立短事务，命令 Service 在自有事务内
 *       调用时并入命令事务（契约 8.2「就地补结算，同事务」）；</li>
 *   <li>settleBatch 不标注事务：候选为无锁快照，逐条经 runner 以独立 guard 短事务结算；</li>
 *   <li>checkout/cancel/adminTerminate 由命令 Service 在已持 guard 的事务内调用，
 *        REQUIRED 并入调用方事务，内部只做 FOR UPDATE 重读，不重复锁 guard；</li>
 *   <li>settleInternal 是共享核心：调用方已持 guard 锁与事务，包内可见（runner 与
 *       PreflightSettlementServiceImpl 复用）。</li>
 * </ul>
 */
@Service
public class SettlementServiceImpl implements SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementServiceImpl.class);

    /** 违约截止点请求键格式：毫秒精度，保证同一截止点重放幂等。 */
    private static final DateTimeFormatter DEADLINE_KEY =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final SettlementTransactionRunner transactionRunner;
    private final ReservationMapper reservationMapper;
    private final AttendanceEventMapper attendanceEventMapper;
    private final SettlementResultMapper settlementResultMapper;
    private final ViolationMapper violationMapper;
    private final CreditService creditService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final BusinessClock clock;

    /**
     * @param transactionRunner 独立事务边界 bean；@Lazy 打破
     *                          Impl → Runner → Impl 的构造期循环依赖
     *                          （Runner 需要本类的 settleInternal，本类需要 Runner 的代理事务）
     */
    public SettlementServiceImpl(@Lazy SettlementTransactionRunner transactionRunner,
                                 ReservationMapper reservationMapper,
                                 AttendanceEventMapper attendanceEventMapper,
                                 SettlementResultMapper settlementResultMapper,
                                 ViolationMapper violationMapper,
                                 CreditService creditService,
                                 NotificationService notificationService,
                                 AuditService auditService,
                                 BusinessClock clock) {
        this.transactionRunner = transactionRunner;
        this.reservationMapper = reservationMapper;
        this.attendanceEventMapper = attendanceEventMapper;
        this.settlementResultMapper = settlementResultMapper;
        this.violationMapper = violationMapper;
        this.creditService = creditService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    public SettlementOutcome settleOne(Long reservationId, LocalDateTime now, String trigger) {
        // 必须经独立 bean 的 Spring 代理调用，self-invocation 不走代理会丢失 @Transactional
        return transactionRunner.settleOneTx(reservationId, now, trigger);
    }

    @Override
    public int settleBatch(LocalDateTime now, int limit) {
        // 不持锁读候选（候选不是授权与状态依据）；逐条经 runner 独立 guard 短事务结算
        List<Reservation> candidates = reservationMapper.selectSettlementCandidates(now, limit);
        int settled = 0;
        for (Reservation candidate : candidates) {
            try {
                SettlementOutcome outcome =
                        transactionRunner.settleOneTx(candidate.getId(), now, "SCHEDULER");
                if (outcome != null) {
                    settled++;
                }
            } catch (Exception e) {
                // 单订单失败只记录脱敏 orderId/traceId 并跳过，不终止整轮；
                // 不在轮内重试同一失败订单，下一轮扫描（60s）重新评估
                log.error("结算候选订单失败，跳过 reservationId={} orderNo={} traceId={}",
                        candidate.getId(), candidate.getOrderNo(), MDC.get("traceId"), e);
            }
        }
        return settled;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public SettlementOutcome checkout(Long reservationId, LocalDateTime now) {
        Reservation reservation = reservationMapper.selectByIdForUpdate(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在");
        }
        if (reservation.getStatus().isTerminal()) {
            return replayTerminal(reservation);
        }
        if (DeadlineRules.endReached(now, reservation.getEndAt())) {
            // 到点签退降级为到期自动完成（零奖励零扣分）
            return settleInternal(reservation, now, "COMMAND");
        }
        if (reservation.getStatus() != ReservationStatus.IN_USE
                && reservation.getStatus() != ReservationStatus.LEFT_TEMP) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前状态不可签退");
        }
        List<AttendanceEvent> events =
                attendanceEventMapper.selectByReservationId(reservationId);
        // IN_USE 的在座段 / LEFT_TEMP 的未闭合段都截止到实际签退时间
        long eligible = EligibleTimeCalculator.eligibleSeconds(events, reservation.getStartAt(),
                reservation.getEndAt(), now);
        RuleConfig rule = RuleConfig.fromJson(reservation.getRuleSnapshot());
        int rewardRequested = rule.rewardForSeconds(eligible);

        writeTerminalEventIfAbsent(reservationId, AttendanceEventType.CHECK_OUT, now,
                "checkout:" + reservationId, null);

        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.setFinishedAt(now);
        reservation.setTerminationReason(TerminationReason.STUDENT_CHECKOUT);
        reservation.setUpdatedAt(now);
        requireOneRow(reservationMapper.updateState(reservation), "签退状态更新");

        // 零奖励不写 REWARD 流水但保存零值 settlement_result；
        // 封顶导致 actual=0 且 requested>0 时仍写奖励流水
        int rewardActual = 0;
        Long creditLogId = null;
        if (rewardRequested > 0) {
            CreditResult credit = creditService.apply(new CreditCommand(
                    "reservation:" + reservationId + ":reward",
                    reservation.getUserId(),
                    rewardRequested,
                    CreditLogType.REWARD,
                    reservationId,
                    null,
                    null,
                    "主动签退奖励（有效在座 " + (eligible / 60) + " 分钟）",
                    null));
            rewardActual = credit.actualDelta();
            creditLogId = credit.logId();
        }

        insertSettlementResult(reservation, "checkout:" + reservationId, now, eligible,
                rewardRequested, rewardActual, creditLogId);
        auditService.record((Long) null, "SETTLE", "RESERVATION", String.valueOf(reservationId),
                settlementSummary("COMMAND", reservation, eligible, rewardRequested, rewardActual,
                        null, creditLogId));
        return new SettlementOutcome(reservationId, ReservationStatus.COMPLETED,
                TerminationReason.STUDENT_CHECKOUT, false, eligible, rewardRequested, rewardActual);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public SettlementOutcome cancel(Long reservationId, LocalDateTime now) {
        Reservation reservation = reservationMapper.selectByIdForUpdate(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在");
        }
        if (reservation.getStatus().isTerminal()) {
            return replayTerminal(reservation);
        }
        // 非 RESERVED 由调用方保证（取消窗口校验在命令层），内部再校验兜底
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅待使用的订单可以取消");
        }
        writeTerminalEventIfAbsent(reservationId, AttendanceEventType.CANCEL, now,
                "cancel:" + reservationId, null);

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setFinishedAt(now);
        reservation.setTerminationReason(TerminationReason.STUDENT_CANCEL);
        reservation.setUpdatedAt(now);
        requireOneRow(reservationMapper.updateState(reservation), "取消状态更新");

        insertSettlementResult(reservation, "cancel:" + reservationId, now, 0L, 0, 0, null);
        auditService.record((Long) null, "SETTLE", "RESERVATION", String.valueOf(reservationId),
                settlementSummary("COMMAND", reservation, 0L, 0, 0, null, null));
        return new SettlementOutcome(reservationId, ReservationStatus.CANCELLED,
                TerminationReason.STUDENT_CANCEL, false, 0L, 0, 0);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public SettlementOutcome adminTerminate(Long reservationId, LocalDateTime now,
                                            TerminationReason reason) {
        if (reason != TerminationReason.ROOM_CLOSED && reason != TerminationReason.SEAT_MAINTENANCE
                && reason != TerminationReason.HOURS_CHANGED
                && reason != TerminationReason.ACCOUNT_DISABLED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "管理员无责终止原因非法");
        }
        Reservation reservation = reservationMapper.selectByIdForUpdate(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在");
        }
        if (reservation.getStatus().isTerminal()) {
            return replayTerminal(reservation);
        }
        switch (reservation.getStatus()) {
            case RESERVED -> {
                // 未使用订单无责取消
                writeTerminalEventIfAbsent(reservationId, AttendanceEventType.CANCEL, now,
                        "admincancel:" + reservationId, null);
                reservation.setStatus(ReservationStatus.CANCELLED);
                reservation.setFinishedAt(now);
                reservation.setTerminationReason(reason);
                reservation.setUpdatedAt(now);
                requireOneRow(reservationMapper.updateState(reservation), "管理员取消状态更新");
                insertSettlementResult(reservation, "admincancel:" + reservationId, now, 0L, 0, 0, null);
                auditService.record((Long) null, "SETTLE", "RESERVATION", String.valueOf(reservationId),
                        settlementSummary("COMMAND", reservation, 0L, 0, 0, null, null));
                return new SettlementOutcome(reservationId, ReservationStatus.CANCELLED, reason,
                        false, 0L, 0, 0);
            }
            case IN_USE, LEFT_TEMP -> {
                // 进行中订单强制结束：零奖励零扣分，在座段截止到强制结束时间
                List<AttendanceEvent> events =
                        attendanceEventMapper.selectByReservationId(reservationId);
                long eligible = EligibleTimeCalculator.eligibleSeconds(events,
                        reservation.getStartAt(), reservation.getEndAt(), now);
                writeTerminalEventIfAbsent(reservationId, AttendanceEventType.FORCE_END, now,
                        "forceend:" + reservationId, null);
                reservation.setStatus(ReservationStatus.COMPLETED);
                reservation.setFinishedAt(now);
                reservation.setTerminationReason(reason);
                reservation.setUpdatedAt(now);
                requireOneRow(reservationMapper.updateState(reservation), "强制结束状态更新");
                insertSettlementResult(reservation, "forceend:" + reservationId, now, eligible, 0, 0, null);
                auditService.record((Long) null, "SETTLE", "RESERVATION", String.valueOf(reservationId),
                        settlementSummary("COMMAND", reservation, eligible, 0, 0, null, null));
                return new SettlementOutcome(reservationId, ReservationStatus.COMPLETED, reason,
                        false, eligible, 0, 0);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_STATE, "当前状态不可强制结束");
        }
    }

    /**
     * 终态结算核心：调用方已持 guard 锁与事务。按最早适用业务截止决策
     * （相等截止 END 优先），写终止事件、状态、违约/信用/通知、结算结果与审计。
     * 仅供本模块内部（SettlementTransactionRunner / PreflightSettlementServiceImpl /
     * checkout / cancel / adminTerminate）经 Spring 代理调用，不作为跨模块 API。
     *
     * @return 结算结果；未到任何截止返回 null
     */
    public SettlementOutcome settleInternal(Reservation reservation, LocalDateTime now, String trigger) {
        Long reservationId = reservation.getId();
        LocalDateTime endAt = reservation.getEndAt();

        // a. 终态订单：幂等重放原结算结果；历史数据（终态无结果）构造零值结果，不写库
        if (reservation.getStatus().isTerminal()) {
            return replayTerminal(reservation);
        }

        // b. 最早适用业务截止决策
        ViolationReason violationReason = null;
        LocalDateTime violationDeadline = null;
        boolean autoEnd = false;
        switch (reservation.getStatus()) {
            case RESERVED -> {
                if (DeadlineRules.noShowDue(now, reservation.getCheckinDueAt(), endAt)) {
                    violationReason = ViolationReason.NO_SHOW;
                    violationDeadline = reservation.getCheckinDueAt();
                } else if (DeadlineRules.endReached(now, endAt)) {
                    // 防御分支：签到截止不早于 end（如超短单）时到 end 自动完成
                    autoEnd = true;
                } else {
                    return null;
                }
            }
            case IN_USE -> {
                if (DeadlineRules.endReached(now, endAt)) {
                    autoEnd = true;
                } else {
                    return null;
                }
            }
            case LEFT_TEMP -> {
                // 暂离截止等于或晚于 end 时，end 自动完成优先，不扣暂离违约
                if (DeadlineRules.leaveTimeoutDue(now, reservation.getLeaveDueAt(), endAt)) {
                    violationReason = ViolationReason.LEAVE_TIMEOUT;
                    violationDeadline = reservation.getLeaveDueAt();
                } else if (DeadlineRules.endReached(now, endAt)) {
                    autoEnd = true;
                } else {
                    return null;
                }
            }
            default -> {
                return null;
            }
        }

        if (violationReason != null) {
            return settleViolation(reservation, now, trigger, violationReason, violationDeadline);
        }
        return settleAutoEnd(reservation, now, trigger);
    }

    // ---------------------------------------------------------------- 内部实现

    /** 违约终态：截止点成立违约，扣分、通知、结算同事务。 */
    private SettlementOutcome settleViolation(Reservation reservation, LocalDateTime now, String trigger,
                                              ViolationReason violationReason,
                                              LocalDateTime deadline) {
        Long reservationId = reservation.getId();
        boolean noShow = violationReason == ViolationReason.NO_SHOW;

        // 有效在座时长先算（一致性异常在任何写库前抛出）：爽约从无在座段；
        // 暂离超时最后 LEAVE 已闭合在座段，截至截止点计算
        long eligible = 0L;
        if (!noShow) {
            List<AttendanceEvent> events =
                    attendanceEventMapper.selectByReservationId(reservationId);
            eligible = EligibleTimeCalculator.eligibleSeconds(events, reservation.getStartAt(), deadline);
        }

        // 1. 终态考勤事件 VIOLATION_END（event_at=截止点，request_key 幂等）
        String eventKey = violationEventKey(reservationId, deadline);
        writeTerminalEventIfAbsent(reservationId, AttendanceEventType.VIOLATION_END, deadline,
                eventKey, null);

        // 2. 状态迁移（version 条件更新）
        TerminationReason terminationReason =
                noShow ? TerminationReason.NO_SHOW : TerminationReason.LEAVE_TIMEOUT;
        reservation.setStatus(ReservationStatus.VIOLATED);
        reservation.setFinishedAt(deadline);
        reservation.setTerminationReason(terminationReason);
        reservation.setUpdatedAt(now);
        requireOneRow(reservationMapper.updateState(reservation), "违约状态更新");

        // 3. 违约记录：event_at=截止点（业务发生时间），processed_at=now（实际处理时间），
        //    notified_at=now（可得知时刻，申诉窗口自此起算 48h）
        Violation violation = new Violation();
        violation.setReservationId(reservationId);
        violation.setUserId(reservation.getUserId());
        violation.setReason(violationReason);
        violation.setStatus(ViolationStatus.ACTIVE);
        violation.setEventAt(deadline);
        violation.setProcessedAt(now);
        violation.setNotifiedAt(now);
        violation.setCreatedAt(now);
        violationMapper.insert(violation);

        // 4. 信用扣减：分值从订单自身 rule_snapshot 解析，禁止使用当前规则
        RuleConfig rule = RuleConfig.fromJson(reservation.getRuleSnapshot());
        int penalty = noShow ? rule.noShowPenalty() : rule.leaveTimeoutPenalty();
        CreditResult credit = creditService.apply(new CreditCommand(
                "reservation:" + reservationId + ":terminal",
                reservation.getUserId(),
                -penalty,
                noShow ? CreditLogType.NO_SHOW : CreditLogType.LEAVE_TIMEOUT,
                reservationId,
                violation.getId(),
                null,
                noShow ? "爽约违约扣减" : "暂离超时违约扣减",
                null));

        // 5. 回填扣分流水
        violationMapper.updateDeductionLog(violation.getId(), credit.logId());

        // 6. 违约通知（与处罚同事务）
        notificationService.publish(reservation.getUserId(),
                "violation:" + violation.getId() + ":deduct",
                "VIOLATION",
                "违约通知",
                violationContent(reservation, violationReason, penalty, credit.balanceAfter()),
                reservationId,
                violation.getId(),
                now);

        // 7. 结算结果
        insertSettlementResult(reservation, eventKey, now, eligible, 0, 0, credit.logId());
        auditService.record((Long) null, "SETTLE", "RESERVATION", String.valueOf(reservationId),
                settlementSummary(trigger, reservation, eligible, 0, 0, violation.getId(),
                        credit.logId()));
        return new SettlementOutcome(reservationId, ReservationStatus.VIOLATED, terminationReason,
                false, eligible, 0, 0);
    }

    /** 到期自动完成：零奖励零扣分。 */
    private SettlementOutcome settleAutoEnd(Reservation reservation, LocalDateTime now, String trigger) {
        Long reservationId = reservation.getId();
        LocalDateTime endAt = reservation.getEndAt();
        List<AttendanceEvent> events = attendanceEventMapper.selectByReservationId(reservationId);
        // 自动结算使用实际业务截止 end_at，不按扫描时刻扩大在座
        long eligible = EligibleTimeCalculator.eligibleSeconds(events, reservation.getStartAt(),
                endAt, endAt);

        String eventKey = "autoend:" + reservationId;
        writeTerminalEventIfAbsent(reservationId, AttendanceEventType.AUTO_END, endAt, eventKey, null);

        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.setFinishedAt(endAt);
        reservation.setTerminationReason(TerminationReason.AUTO_END);
        reservation.setUpdatedAt(now);
        requireOneRow(reservationMapper.updateState(reservation), "到期自动完成状态更新");

        insertSettlementResult(reservation, eventKey, now, eligible, 0, 0, null);
        auditService.record((Long) null, "SETTLE", "RESERVATION", String.valueOf(reservationId),
                settlementSummary(trigger, reservation, eligible, 0, 0, null, null));
        return new SettlementOutcome(reservationId, ReservationStatus.COMPLETED,
                TerminationReason.AUTO_END, false, eligible, 0, 0);
    }

    /** 终态订单重放：有结算结果按原值，否则（历史数据防御）零值结果。 */
    private SettlementOutcome replayTerminal(Reservation reservation) {
        Long reservationId = reservation.getId();
        SettlementResult existing = settlementResultMapper.selectByReservationId(reservationId);
        if (existing == null) {
            return new SettlementOutcome(reservationId, reservation.getStatus(),
                    reservation.getTerminationReason(), true, 0L, 0, 0);
        }
        return new SettlementOutcome(reservationId, reservation.getStatus(), existing.getReason(), true,
                existing.getEligibleSeconds() == null ? 0L : existing.getEligibleSeconds(),
                existing.getRewardRequested() == null ? 0 : existing.getRewardRequested(),
                existing.getRewardActual() == null ? 0 : existing.getRewardActual());
    }

    /** 终态考勤事件幂等写：UQ(reservation_id, request_key) 保证一个订单一次终态事件。 */
    private void writeTerminalEventIfAbsent(Long reservationId, AttendanceEventType type,
                                            LocalDateTime eventAt, String requestKey, Long operatorId) {
        if (attendanceEventMapper.selectByReservationAndRequestKey(reservationId, requestKey) != null) {
            return;
        }
        AttendanceEvent event = new AttendanceEvent();
        event.setReservationId(reservationId);
        event.setEventType(type);
        event.setEventAt(eventAt);
        event.setOperatorId(operatorId);
        event.setRequestKey(requestKey);
        event.setCreatedAt(clock.nowLocal());
        attendanceEventMapper.insert(event);
    }

    private void insertSettlementResult(Reservation reservation, String terminalEventKey,
                                        LocalDateTime now, long eligibleSeconds, int rewardRequested,
                                        int rewardActual, Long creditLogId) {
        SettlementResult result = new SettlementResult();
        result.setReservationId(reservation.getId());
        result.setTerminalEventKey(terminalEventKey);
        result.setReason(reservation.getTerminationReason());
        result.setFinishedAt(reservation.getFinishedAt());
        result.setEligibleSeconds(eligibleSeconds);
        result.setRewardRequested(rewardRequested);
        result.setRewardActual(rewardActual);
        result.setCreditLogId(creditLogId);
        result.setCreatedAt(now);
        settlementResultMapper.insert(result);
    }

    private static String violationEventKey(Long reservationId, LocalDateTime deadline) {
        return "settle:" + reservationId + ":" + deadline.format(DEADLINE_KEY);
    }

    private static void requireOneRow(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE, action + "失败，订单状态已变化，请重试");
        }
    }

    private static String violationContent(Reservation reservation, ViolationReason reason, int penalty,
                                           int balanceAfter) {
        String reasonText = reason == ViolationReason.NO_SHOW ? "未按时签到（爽约）" : "暂离超时未回座";
        return "您的预约「" + reservation.getOrderNo() + "」（" + reservation.getRoomNameSnapshot() + " "
                + reservation.getSeatCodeSnapshot() + "，" + reservation.getStartAt() + " 至 "
                + reservation.getEndAt() + "）因" + reasonText + "已终止，信用扣减 " + penalty
                + " 分，当前信用 " + balanceAfter + " 分。如有异议，请在 48 小时内提交申诉。";
    }

    /** 脱敏审计摘要：仅含枚举/数字/ID，trigger 过滤为安全字符，summary 列是 JSON 类型。 */
    private static String settlementSummary(String trigger, Reservation reservation, long eligible,
                                            int rewardRequested, int rewardActual, Long violationId,
                                            Long creditLogId) {
        String safeTrigger = trigger == null ? "" : trigger.replaceAll("[^A-Za-z0-9_]", "");
        return "{\"trigger\":\"" + safeTrigger + "\",\"status\":\"" + reservation.getStatus()
                + "\",\"reason\":\"" + reservation.getTerminationReason() + "\",\"finishedAt\":\""
                + reservation.getFinishedAt() + "\",\"eligibleSeconds\":" + eligible
                + ",\"rewardRequested\":" + rewardRequested + ",\"rewardActual\":" + rewardActual
                + ",\"violationId\":" + (violationId == null ? "null" : violationId)
                + ",\"creditLogId\":" + (creditLogId == null ? "null" : creditLogId) + "}";
    }
}
