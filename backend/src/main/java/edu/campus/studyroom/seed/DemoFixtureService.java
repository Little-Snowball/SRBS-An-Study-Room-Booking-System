package edu.campus.studyroom.seed;

import edu.campus.studyroom.appeal.domain.Appeal;
import edu.campus.studyroom.appeal.domain.AppealFile;
import edu.campus.studyroom.appeal.domain.AppealStatus;
import edu.campus.studyroom.appeal.mapper.AppealFileMapper;
import edu.campus.studyroom.appeal.mapper.AppealMapper;
import edu.campus.studyroom.audit.AuditService;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.Ids;
import edu.campus.studyroom.common.domain.CommandDedup;
import edu.campus.studyroom.common.mapper.CommandDedupMapper;
import edu.campus.studyroom.credit.CreditCommand;
import edu.campus.studyroom.credit.CreditResult;
import edu.campus.studyroom.credit.CreditService;
import edu.campus.studyroom.credit.domain.CreditAccount;
import edu.campus.studyroom.credit.domain.CreditLogType;
import edu.campus.studyroom.credit.mapper.CreditAccountMapper;
import edu.campus.studyroom.file.domain.FileObject;
import edu.campus.studyroom.file.domain.FileState;
import edu.campus.studyroom.file.mapper.FileObjectMapper;
import edu.campus.studyroom.notice.domain.Notice;
import edu.campus.studyroom.notice.domain.NoticeStatus;
import edu.campus.studyroom.notice.mapper.NoticeMapper;
import edu.campus.studyroom.notification.NotificationService;
import edu.campus.studyroom.reservation.domain.AttendanceEvent;
import edu.campus.studyroom.reservation.domain.AttendanceEventType;
import edu.campus.studyroom.reservation.domain.DeadlineRules;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.domain.TerminationReason;
import edu.campus.studyroom.reservation.domain.TimeGrid;
import edu.campus.studyroom.reservation.domain.TimeRange;
import edu.campus.studyroom.reservation.mapper.AttendanceEventMapper;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.resource.domain.RoomStatus;
import edu.campus.studyroom.resource.domain.Seat;
import edu.campus.studyroom.resource.domain.SeatStatus;
import edu.campus.studyroom.resource.domain.StudyRoom;
import edu.campus.studyroom.resource.mapper.SeatMapper;
import edu.campus.studyroom.resource.mapper.StudyRoomMapper;
import edu.campus.studyroom.rule.BusinessGuardLock;
import edu.campus.studyroom.rule.RuleService;
import edu.campus.studyroom.rule.domain.RuleConfig;
import edu.campus.studyroom.rule.domain.RuleVersion;
import edu.campus.studyroom.settlement.domain.SettlementResult;
import edu.campus.studyroom.settlement.domain.Violation;
import edu.campus.studyroom.settlement.domain.ViolationReason;
import edu.campus.studyroom.settlement.domain.ViolationStatus;
import edu.campus.studyroom.settlement.mapper.SettlementResultMapper;
import edu.campus.studyroom.settlement.mapper.ViolationMapper;
import edu.campus.studyroom.user.domain.AppUser;
import edu.campus.studyroom.user.domain.UserRole;
import edu.campus.studyroom.user.mapper.AppUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Demo fixture importer (local/test only, explicit --allow-demo-fixtures).
 * Builds a full historical business picture relative to the run day: terminal
 * orders of every kind, attendance events, credit logs (including a capped
 * reward, a revoked violation with refund and a pending appeal with evidence
 * metadata), a pinned published notice plus a notification. All rows land in
 * one guarded READ_COMMITTED transaction; every business sample carries a
 * deterministic demo_key in command_dedup so re-runs skip instead of
 * duplicating. After the import, invariants are verified (no effective
 * overlap, credit 0-100 continuous, at most one terminal settlement per
 * order); any violation rolls the whole import back.
 *
 * <p>File choice: the pending appeal binds a file_object metadata row only
 * (state BOUND) — demo environments have no real upload bytes; the upload
 * pipeline itself belongs to the file module.
 */
@Service
public class DemoFixtureService {

    private static final Logger log = LoggerFactory.getLogger(DemoFixtureService.class);

    private static final String COMMAND_TYPE = "DEMO_FIXTURE";
    private static final String[] STUDENT_ACCOUNTS = {
            "20240001", "20240002", "20240003", "20240004",
            "20240005", "20240006", "20240007", "20240008"};
    private static final String NOTICE_TITLE = "系统使用指南与演示数据说明";
    private static final String NOTICE_CONTENT = "欢迎使用高校自习室预约与考勤管理系统。本系统支持分时预约、现场码签到、"
            + "暂离管控与违约申诉。当前数据库包含演示参考数据与演示业务流水，仅供课程设计演示与测试使用；"
            + "演示学生的初始密码见部署文档，管理员首次登录后必须修改初始密码。如有疑问请联系管理员。";

    private final BusinessGuardLock businessGuardLock;
    private final StudyRoomMapper studyRoomMapper;
    private final SeatMapper seatMapper;
    private final AppUserMapper appUserMapper;
    private final ReservationMapper reservationMapper;
    private final AttendanceEventMapper attendanceEventMapper;
    private final ViolationMapper violationMapper;
    private final SettlementResultMapper settlementResultMapper;
    private final CreditAccountMapper creditAccountMapper;
    private final CreditService creditService;
    private final AppealMapper appealMapper;
    private final AppealFileMapper appealFileMapper;
    private final FileObjectMapper fileObjectMapper;
    private final NoticeMapper noticeMapper;
    private final NotificationService notificationService;
    private final CommandDedupMapper commandDedupMapper;
    private final RuleService ruleService;
    private final AuditService auditService;
    private final BusinessClock clock;

    public DemoFixtureService(BusinessGuardLock businessGuardLock, StudyRoomMapper studyRoomMapper,
                              SeatMapper seatMapper, AppUserMapper appUserMapper,
                              ReservationMapper reservationMapper, AttendanceEventMapper attendanceEventMapper,
                              ViolationMapper violationMapper, SettlementResultMapper settlementResultMapper,
                              CreditAccountMapper creditAccountMapper, CreditService creditService,
                              AppealMapper appealMapper, AppealFileMapper appealFileMapper,
                              FileObjectMapper fileObjectMapper, NoticeMapper noticeMapper,
                              NotificationService notificationService, CommandDedupMapper commandDedupMapper,
                              RuleService ruleService, AuditService auditService, BusinessClock clock) {
        this.businessGuardLock = businessGuardLock;
        this.studyRoomMapper = studyRoomMapper;
        this.seatMapper = seatMapper;
        this.appUserMapper = appUserMapper;
        this.reservationMapper = reservationMapper;
        this.attendanceEventMapper = attendanceEventMapper;
        this.violationMapper = violationMapper;
        this.settlementResultMapper = settlementResultMapper;
        this.creditAccountMapper = creditAccountMapper;
        this.creditService = creditService;
        this.appealMapper = appealMapper;
        this.appealFileMapper = appealFileMapper;
        this.fileObjectMapper = fileObjectMapper;
        this.noticeMapper = noticeMapper;
        this.notificationService = notificationService;
        this.commandDedupMapper = commandDedupMapper;
        this.ruleService = ruleService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Resolved seed context shared by all sample builders. */
    private final class Ctx {
        final LocalDateTime now;
        final LocalDate today;
        final LocalDate yesterday;
        final LocalDateTime openAt;
        final LocalDateTime closeAt;
        final StudyRoom room;
        final List<Seat> seats;
        final AppUser admin;
        final Map<String, AppUser> users;
        final RuleConfig rule;
        final Long ruleVersionId;
        final String ruleSnapshot;
        final Counters counters = new Counters();
        final Set<Long> seatsUsed = new HashSet<>();
        final Set<Long> usersUsed = new HashSet<>();
        final Map<Long, Integer> expectedScore = new HashMap<>();

        Ctx(LocalDateTime now, StudyRoom room, List<Seat> seats, AppUser admin,
            Map<String, AppUser> users, RuleConfig rule, Long ruleVersionId, String ruleSnapshot) {
            this.now = now;
            this.today = now.toLocalDate();
            this.yesterday = today.minusDays(1);
            this.openAt = LocalDateTime.of(today, room.getOpenTime());
            this.closeAt = LocalDateTime.of(today, room.getCloseTime());
            this.room = room;
            this.seats = seats;
            this.admin = admin;
            this.users = users;
            this.rule = rule;
            this.ruleVersionId = ruleVersionId;
            this.ruleSnapshot = ruleSnapshot;
        }

        AppUser user(String account) {
            AppUser user = users.get(account);
            if (user == null) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "缺少演示学生账号 " + account + "，请先执行 --seed-reference-data");
            }
            return user;
        }

        Seat seat(int index) {
            return seats.get(index);
        }

        String demoKey(LocalDate date, String kind) {
            return "demo:" + date + ":" + kind;
        }
    }

    private static final class Counters {
        int created;
        int skipped;
    }

    /**
     * Imports the demo fixtures atomically. Returns the created/skipped counters;
     * any invariant failure aborts the transaction with a rollback.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SeedReport importFixtures() {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();

        StudyRoom room = resolveOpenRoom();
        List<Seat> seats = seatMapper.selectByRoomId(room.getId()).stream()
                .filter(seat -> seat.getStatus() == SeatStatus.NORMAL)
                .sorted(Comparator.comparing(Seat::getCode))
                .toList();
        if (seats.size() < 6) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "开放房间 " + room.getCode() + " 的可用座位不足 6 个，无法生成演示夹具");
        }
        AppUser admin = resolveAdmin();
        Map<String, AppUser> users = resolveStudents();
        RuleConfig rule = ruleService.activeRule();
        RuleVersion ruleVersion = ruleService.activeVersion();
        Long ruleVersionId = ruleVersion.getId();
        String ruleSnapshot = ruleVersion.getConfigJson();

        Ctx c = new Ctx(now, room, seats, admin, users, rule, ruleVersionId, ruleSnapshot);

        // 历史样本（昨天的完整业务图）
        seedCompletedCheckout(c);
        seedCompletedAutoEnd(c);
        seedCancelled(c);
        seedNoShowActive(c);
        seedLeaveTimeoutRevoked(c);
        seedNoShowWithAppeal(c);

        // 当天样本：仅当当前时刻处于营业时间内才生成
        if (!now.isBefore(c.openAt) && now.isBefore(c.closeAt)) {
            seedReservedToday(c);
            seedInUseToday(c);
            seedLeftTempToday(c);
        } else {
            log.info("当前时刻 {} 不在房间 {} 的营业时间 {}—{} 内，跳过进行中样本"
                            + "（RESERVED / IN_USE / LEFT_TEMP）",
                    now, room.getCode(), room.getOpenTime(), room.getCloseTime());
        }

        seedPinnedNotice(c);

        verifyInvariants(c);

        auditService.record((Long) null, "DEMO_FIXTURE_IMPORT", "SYSTEM", "DEMO_FIXTURE",
                "{\"created\":" + c.counters.created + ",\"skipped\":" + c.counters.skipped + "}");
        String detail = String.format("样本创建 %d，幂等跳过 %d（房间 %s，运行日 %s）",
                c.counters.created, c.counters.skipped, room.getCode(), c.today);
        log.info("演示夹具导入完成：{}", detail);
        return new SeedReport(c.counters.created, c.counters.skipped, detail);
    }

    // ---------------------------------------------------------------- samples

    /** 昨日 09:00—12:00 主动签退完成；3 小时在座奖励触发 0—100 封顶（requested=4, actual=0）。 */
    private void seedCompletedCheckout(Ctx c) {
        String key = c.demoKey(c.yesterday, "COMPLETED_CHECKOUT");
        AppUser user = c.user("20240001");
        Seat seat = c.seat(0);
        if (skipIfDone(c, user.getId(), key)) {
            return;
        }
        LocalDateTime start = LocalDateTime.of(c.yesterday, LocalTime.of(9, 0));
        LocalDateTime end = LocalDateTime.of(c.yesterday, LocalTime.of(12, 0));
        long reservationId = insertReservation(c, user, seat, start, end, ReservationStatus.COMPLETED,
                start, null, end, TerminationReason.STUDENT_CHECKOUT);
        insertEvent(c, reservationId, AttendanceEventType.CHECK_IN, start, user.getId(), key + ":CHECK_IN");
        insertEvent(c, reservationId, AttendanceEventType.CHECK_OUT, end, user.getId(), key + ":CHECK_OUT");
        long eligibleSeconds = Duration.between(start, end).toSeconds();
        int requested = c.rule.rewardForSeconds(eligibleSeconds);
        CreditResult reward = applyCredit(c, user.getId(), reservationId, null, requested,
                CreditLogType.REWARD, key + ":REWARD",
                "演示数据：完成 " + (eligibleSeconds / 60) + " 分钟自习的在座奖励（余额封顶）");
        insertSettlement(c, reservationId, key + ":SETTLE", TerminationReason.STUDENT_CHECKOUT, end,
                eligibleSeconds, requested, reward.actualDelta(), reward.logId());
        recordDedup(c, user.getId(), key, "RESERVATION", String.valueOf(reservationId));
        c.counters.created++;
    }

    /** 昨日 14:00—15:30 到期自动完成；在座不足奖励起点，奖励为 0。 */
    private void seedCompletedAutoEnd(Ctx c) {
        String key = c.demoKey(c.yesterday, "COMPLETED_AUTO_END");
        AppUser user = c.user("20240002");
        Seat seat = c.seat(1);
        if (skipIfDone(c, user.getId(), key)) {
            return;
        }
        LocalDateTime start = LocalDateTime.of(c.yesterday, LocalTime.of(14, 0));
        LocalDateTime end = LocalDateTime.of(c.yesterday, LocalTime.of(15, 30));
        long reservationId = insertReservation(c, user, seat, start, end, ReservationStatus.COMPLETED,
                start, null, end, TerminationReason.AUTO_END);
        insertEvent(c, reservationId, AttendanceEventType.CHECK_IN, start, user.getId(), key + ":CHECK_IN");
        insertEvent(c, reservationId, AttendanceEventType.AUTO_END, end, null, key + ":AUTO_END");
        long eligibleSeconds = Duration.between(start, end).toSeconds();
        insertSettlement(c, reservationId, key + ":SETTLE", TerminationReason.AUTO_END, end,
                eligibleSeconds, 0, 0, null);
        recordDedup(c, user.getId(), key, "RESERVATION", String.valueOf(reservationId));
        c.counters.created++;
    }

    /** 昨日 19:00—21:00 用户在无损取消截止前取消。 */
    private void seedCancelled(Ctx c) {
        String key = c.demoKey(c.yesterday, "CANCELLED");
        AppUser user = c.user("20240006");
        Seat seat = c.seat(2);
        if (skipIfDone(c, user.getId(), key)) {
            return;
        }
        LocalDateTime start = LocalDateTime.of(c.yesterday, LocalTime.of(19, 0));
        LocalDateTime end = LocalDateTime.of(c.yesterday, LocalTime.of(21, 0));
        LocalDateTime cancelledAt = LocalDateTime.of(c.yesterday, LocalTime.of(18, 30));
        long reservationId = insertReservation(c, user, seat, start, end, ReservationStatus.CANCELLED,
                null, null, cancelledAt, TerminationReason.STUDENT_CANCEL);
        insertEvent(c, reservationId, AttendanceEventType.CANCEL, cancelledAt, user.getId(), key + ":CANCEL");
        insertSettlement(c, reservationId, key + ":SETTLE", TerminationReason.STUDENT_CANCEL,
                cancelledAt, 0, 0, 0, null);
        recordDedup(c, user.getId(), key, "RESERVATION", String.valueOf(reservationId));
        c.counters.created++;
    }

    /** 昨日 10:00—12:00 爽约：ACTIVE 违约 + 扣分流水 + 通知。 */
    private void seedNoShowActive(Ctx c) {
        String key = c.demoKey(c.yesterday, "NO_SHOW_ACTIVE");
        AppUser user = c.user("20240003");
        Seat seat = c.seat(3);
        if (skipIfDone(c, user.getId(), key)) {
            return;
        }
        LocalDateTime start = LocalDateTime.of(c.yesterday, LocalTime.of(10, 0));
        LocalDateTime end = LocalDateTime.of(c.yesterday, LocalTime.of(12, 0));
        LocalDateTime due = DeadlineRules.checkinDue(start, c.rule, end);
        long reservationId = insertReservation(c, user, seat, start, end, ReservationStatus.VIOLATED,
                null, null, due, TerminationReason.NO_SHOW);
        insertEvent(c, reservationId, AttendanceEventType.VIOLATION_END, due, null, key + ":VIOLATION_END");
        long violationId = insertViolation(c, reservationId, user, ViolationReason.NO_SHOW, due);
        CreditResult deduction = applyCredit(c, user.getId(), reservationId, violationId,
                -c.rule.noShowPenalty(), CreditLogType.NO_SHOW, key + ":DEDUCT",
                "演示数据：爽约未签到，按规则扣 " + c.rule.noShowPenalty() + " 分");
        violationMapper.updateDeductionLog(violationId, deduction.logId());
        notificationService.publish(user.getId(), key + ":NOTICE", "VIOLATION", "违约通知：爽约未签到",
                "您预约的 " + c.room.getName() + " " + seat.getCode() + "（" + start + "）未按时签到，"
                        + "已按规则扣 " + c.rule.noShowPenalty() + " 分。如有异议请在申诉期内提交申诉。",
                reservationId, violationId, due);
        insertSettlement(c, reservationId, key + ":SETTLE", TerminationReason.NO_SHOW, due, 0, 0, 0, null);
        recordDedup(c, user.getId(), key, "RESERVATION", String.valueOf(reservationId));
        c.counters.created++;
    }

    /** 昨日 13:00—16:00 暂离超时：扣分后处罚被撤销（退还流水），演示 REVOKED 全链路。 */
    private void seedLeaveTimeoutRevoked(Ctx c) {
        String key = c.demoKey(c.yesterday, "LEAVE_TIMEOUT_REVOKED");
        AppUser user = c.user("20240004");
        Seat seat = c.seat(4);
        if (skipIfDone(c, user.getId(), key)) {
            return;
        }
        LocalDateTime start = LocalDateTime.of(c.yesterday, LocalTime.of(13, 0));
        LocalDateTime end = LocalDateTime.of(c.yesterday, LocalTime.of(16, 0));
        LocalDateTime leaveAt = LocalDateTime.of(c.yesterday, LocalTime.of(14, 0));
        LocalDateTime due = leaveAt.plusMinutes(c.rule.leaveMaxMinutes());
        long reservationId = insertReservation(c, user, seat, start, end, ReservationStatus.VIOLATED,
                start, leaveAt, due, TerminationReason.LEAVE_TIMEOUT);
        insertEvent(c, reservationId, AttendanceEventType.CHECK_IN, start, user.getId(), key + ":CHECK_IN");
        insertEvent(c, reservationId, AttendanceEventType.LEAVE, leaveAt, user.getId(), key + ":LEAVE");
        insertEvent(c, reservationId, AttendanceEventType.VIOLATION_END, due, null, key + ":VIOLATION_END");
        long violationId = insertViolation(c, reservationId, user, ViolationReason.LEAVE_TIMEOUT, due);
        CreditResult deduction = applyCredit(c, user.getId(), reservationId, violationId,
                -c.rule.leaveTimeoutPenalty(), CreditLogType.LEAVE_TIMEOUT, key + ":DEDUCT",
                "演示数据：暂离超时未归座，按规则扣 " + c.rule.leaveTimeoutPenalty() + " 分");
        violationMapper.updateDeductionLog(violationId, deduction.logId());
        notificationService.publish(user.getId(), key + ":NOTICE", "VIOLATION", "违约通知：暂离超时",
                "您预约的 " + c.room.getName() + " " + seat.getCode() + " 暂离后未在 "
                        + c.rule.leaveMaxMinutes() + " 分钟内归座，已按规则扣 "
                        + c.rule.leaveTimeoutPenalty() + " 分。如有异议请在申诉期内提交申诉。",
                reservationId, violationId, due);
        LocalDateTime revokedAt = LocalDateTime.of(c.yesterday, LocalTime.of(16, 0));
        int revoked = violationMapper.updateRevoked(violationId, ViolationStatus.ACTIVE, revokedAt,
                c.admin.getId());
        if (revoked != 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "演示数据撤销处罚失败：violation=" + violationId);
        }
        CreditResult refund = applyCredit(c, user.getId(), reservationId, violationId,
                c.rule.leaveTimeoutPenalty(), CreditLogType.APPEAL_REFUND, key + ":REFUND",
                "演示数据：处罚被管理员撤销，退还暂离超时扣分", deduction.logId());
        notificationService.publish(user.getId(), key + ":REFUND_NOTICE", "VIOLATION", "处罚已撤销",
                "您此前的暂离超时处罚经管理员核实后已撤销，扣分已退还，当前信用分以个人中心为准。",
                reservationId, violationId, revokedAt);
        long eligibleSeconds = Duration.between(start, leaveAt).toSeconds();
        insertSettlement(c, reservationId, key + ":SETTLE", TerminationReason.LEAVE_TIMEOUT, due,
                eligibleSeconds, 0, 0, null);
        recordDedup(c, user.getId(), key, "RESERVATION", String.valueOf(reservationId));
        c.counters.created++;
    }

    /** 昨日 20:00—22:00 爽约：ACTIVE 违约 + 待审申诉（绑定凭证元数据）。 */
    private void seedNoShowWithAppeal(Ctx c) {
        String key = c.demoKey(c.yesterday, "NO_SHOW_APPEAL");
        AppUser user = c.user("20240007");
        Seat seat = c.seat(5);
        if (skipIfDone(c, user.getId(), key)) {
            return;
        }
        LocalDateTime start = LocalDateTime.of(c.yesterday, LocalTime.of(20, 0));
        LocalDateTime end = LocalDateTime.of(c.yesterday, LocalTime.of(22, 0));
        LocalDateTime due = DeadlineRules.checkinDue(start, c.rule, end);
        long reservationId = insertReservation(c, user, seat, start, end, ReservationStatus.VIOLATED,
                null, null, due, TerminationReason.NO_SHOW);
        insertEvent(c, reservationId, AttendanceEventType.VIOLATION_END, due, null, key + ":VIOLATION_END");
        long violationId = insertViolation(c, reservationId, user, ViolationReason.NO_SHOW, due);
        CreditResult deduction = applyCredit(c, user.getId(), reservationId, violationId,
                -c.rule.noShowPenalty(), CreditLogType.NO_SHOW, key + ":DEDUCT",
                "演示数据：爽约未签到，按规则扣 " + c.rule.noShowPenalty() + " 分");
        violationMapper.updateDeductionLog(violationId, deduction.logId());
        notificationService.publish(user.getId(), key + ":NOTICE", "VIOLATION", "违约通知：爽约未签到",
                "您预约的 " + c.room.getName() + " " + seat.getCode() + "（" + start + "）未按时签到，"
                        + "已按规则扣 " + c.rule.noShowPenalty() + " 分。如有异议请在申诉期内提交申诉。",
                reservationId, violationId, due);

        LocalDateTime submittedAt = due.plusMinutes(15);
        Appeal appeal = new Appeal();
        appeal.setViolationId(violationId);
        appeal.setUserId(user.getId());
        appeal.setReason("演示数据：当天突发身体不适未能到场，已上传校医院诊断证明作为凭证，"
                + "申请撤销本次爽约处罚并退还扣分。");
        appeal.setStatus(AppealStatus.PENDING);
        appeal.setSubmittedAt(submittedAt);
        appealMapper.insert(appeal);

        // 演示环境不落地真实文件字节，仅写入凭证元数据并完成绑定（上传流程归属 file 模块）。
        FileObject file = new FileObject();
        file.setOwnerId(user.getId());
        file.setStorageKey(UUID.randomUUID().toString());
        file.setOriginalName("校医院诊断证明.jpg");
        file.setMimeType("image/jpeg");
        file.setSizeBytes(204_800L);
        file.setSha256(Ids.sha256Hex(key));
        file.setState(FileState.BOUND);
        file.setCreatedAt(submittedAt);
        fileObjectMapper.insert(file);

        AppealFile appealFile = new AppealFile();
        appealFile.setAppealId(appeal.getId());
        appealFile.setFileId(file.getId());
        appealFileMapper.insert(appealFile);

        insertSettlement(c, reservationId, key + ":SETTLE", TerminationReason.NO_SHOW, due, 0, 0, 0, null);
        recordDedup(c, user.getId(), key, "RESERVATION", String.valueOf(reservationId));
        c.counters.created++;
    }

    /** 当天未来时段的一个 RESERVED 样本（运行时刻须在营业时间内）。 */
    private void seedReservedToday(Ctx c) {
        String key = c.demoKey(c.today, "RESERVED_TODAY");
        AppUser user = c.user("20240001");
        Seat seat = c.seat(0);
        if (skipIfDone(c, user.getId(), key)) {
            return;
        }
        LocalDateTime start = TimeGrid.ceil(c.now, c.rule.slotMinutes());
        LocalDateTime end = min(start.plusHours(2), c.closeAt);
        if (start.isBefore(c.openAt) || !end.isAfter(c.now) || !start.isBefore(end)) {
            log.info("演示夹具跳过：当前时刻临近闭馆，无法安排当天 RESERVED 样本 now={}", c.now);
            c.counters.skipped++;
            return;
        }
        long reservationId = insertReservation(c, user, seat, start, end, ReservationStatus.RESERVED,
                null, null, null, null);
        recordDedup(c, user.getId(), key, "RESERVATION", String.valueOf(reservationId));
        c.counters.created++;
    }

    /** 当天进行中的 IN_USE 样本（已签到，跨当前时刻）。 */
    private void seedInUseToday(Ctx c) {
        String key = c.demoKey(c.today, "IN_USE_TODAY");
        AppUser user = c.user("20240002");
        Seat seat = c.seat(1);
        if (skipIfDone(c, user.getId(), key)) {
            return;
        }
        LocalDateTime start = floorToGrid(c.now, c.rule.slotMinutes());
        LocalDateTime end = min(start.plusHours(2), c.closeAt);
        if (start.isBefore(c.openAt) || !end.isAfter(c.now) || !start.isBefore(end)) {
            log.info("演示夹具跳过：当前时刻不满足 IN_USE 样本条件 now={}", c.now);
            c.counters.skipped++;
            return;
        }
        long reservationId = insertReservation(c, user, seat, start, end, ReservationStatus.IN_USE,
                start, null, null, null);
        insertEvent(c, reservationId, AttendanceEventType.CHECK_IN, start, user.getId(), key + ":CHECK_IN");
        recordDedup(c, user.getId(), key, "RESERVATION", String.valueOf(reservationId));
        c.counters.created++;
    }

    /** 当天进行中的 LEFT_TEMP 样本（已暂离，未到暂离截止）。 */
    private void seedLeftTempToday(Ctx c) {
        String key = c.demoKey(c.today, "LEFT_TEMP_TODAY");
        AppUser user = c.user("20240006");
        Seat seat = c.seat(4);
        if (skipIfDone(c, user.getId(), key)) {
            return;
        }
        LocalDateTime start = max(c.openAt, floorToGrid(c.now, c.rule.slotMinutes()).minusMinutes(90));
        LocalDateTime end = min(start.plusHours(4), c.closeAt);
        LocalDateTime lastLeave = c.now.minusMinutes(20);
        if (!start.isBefore(c.now) || !end.isAfter(c.now) || !lastLeave.isAfter(start)) {
            log.info("演示夹具跳过：当前时刻不满足 LEFT_TEMP 样本条件 now={}", c.now);
            c.counters.skipped++;
            return;
        }
        LocalDateTime leaveDue = lastLeave.plusMinutes(c.rule.leaveMaxMinutes());
        if (!leaveDue.isBefore(end)) {
            log.info("演示夹具跳过：暂离截止已接近订单结束，无法安排 LEFT_TEMP 样本 now={}", c.now);
            c.counters.skipped++;
            return;
        }
        long reservationId = insertReservation(c, user, seat, start, end, ReservationStatus.LEFT_TEMP,
                start, lastLeave, null, null);
        insertEvent(c, reservationId, AttendanceEventType.CHECK_IN, start, user.getId(), key + ":CHECK_IN");
        insertEvent(c, reservationId, AttendanceEventType.LEAVE, lastLeave, user.getId(), key + ":LEAVE");
        recordDedup(c, user.getId(), key, "RESERVATION", String.valueOf(reservationId));
        c.counters.created++;
    }

    /** 一条置顶 PUBLISHED 公告 + 给示例学生的通知。 */
    private void seedPinnedNotice(Ctx c) {
        String key = c.demoKey(c.today, "PINNED_NOTICE");
        if (skipIfDone(c, c.admin.getId(), key)) {
            return;
        }
        Notice notice = new Notice();
        notice.setTitle(NOTICE_TITLE);
        notice.setContent(NOTICE_CONTENT);
        notice.setPinned(true);
        notice.setStatus(NoticeStatus.PUBLISHED);
        notice.setPublishedAt(c.now);
        notice.setCreatedBy(c.admin.getId());
        notice.setVersion(0L);
        notice.setCreatedAt(c.now);
        notice.setUpdatedAt(c.now);
        noticeMapper.insert(notice);
        notificationService.publish(c.user("20240001").getId(), key + ":NOTICE", "NOTICE",
                NOTICE_TITLE, "管理员发布了新公告：" + NOTICE_TITLE, null, null, c.now);
        recordDedup(c, c.admin.getId(), key, "NOTICE", String.valueOf(notice.getId()));
        c.counters.created++;
    }

    // ---------------------------------------------------------------- helpers

    private long insertReservation(Ctx c, AppUser user, Seat seat, LocalDateTime start, LocalDateTime end,
                                   ReservationStatus status, LocalDateTime checkInAt,
                                   LocalDateTime lastLeaveAt, LocalDateTime finishedAt,
                                   TerminationReason reason) {
        Reservation reservation = new Reservation();
        reservation.setOrderNo(Ids.newOrderNo(c.now));
        reservation.setUserId(user.getId());
        reservation.setRoomId(c.room.getId());
        reservation.setSeatId(seat.getId());
        reservation.setRoomNameSnapshot(c.room.getName());
        reservation.setSeatCodeSnapshot(seat.getCode());
        reservation.setStartAt(start);
        reservation.setEndAt(end);
        reservation.setStatus(status);
        reservation.setRuleVersionId(c.ruleVersionId);
        reservation.setRuleSnapshot(c.ruleSnapshot);
        reservation.setCheckinDueAt(DeadlineRules.checkinDue(start, c.rule, end));
        reservation.setCancelDueAt(start.minusMinutes(c.rule.cancelBeforeMinutes()));
        reservation.setCheckInAt(checkInAt);
        reservation.setLastLeaveAt(lastLeaveAt);
        reservation.setLeaveDueAt(lastLeaveAt == null ? null
                : lastLeaveAt.plusMinutes(c.rule.leaveMaxMinutes()));
        reservation.setFinishedAt(finishedAt);
        reservation.setLeaveCount(lastLeaveAt == null ? 0 : 1);
        reservation.setTerminationReason(reason);
        reservation.setVersion(0L);
        reservation.setCreatedAt(c.now);
        reservation.setUpdatedAt(c.now);
        reservationMapper.insert(reservation);
        c.seatsUsed.add(seat.getId());
        c.usersUsed.add(user.getId());
        return reservation.getId();
    }

    private void insertEvent(Ctx c, long reservationId, AttendanceEventType type, LocalDateTime at,
                             Long operatorId, String requestKey) {
        AttendanceEvent event = new AttendanceEvent();
        event.setReservationId(reservationId);
        event.setEventType(type);
        event.setEventAt(at);
        event.setOperatorId(operatorId);
        event.setRequestKey(requestKey);
        event.setCreatedAt(c.now);
        attendanceEventMapper.insert(event);
    }

    private void insertSettlement(Ctx c, long reservationId, String terminalEventKey,
                                  TerminationReason reason, LocalDateTime finishedAt, long eligibleSeconds,
                                  int rewardRequested, int rewardActual, Long creditLogId) {
        SettlementResult result = new SettlementResult();
        result.setReservationId(reservationId);
        result.setTerminalEventKey(terminalEventKey);
        result.setReason(reason);
        result.setFinishedAt(finishedAt);
        result.setEligibleSeconds(eligibleSeconds);
        result.setRewardRequested(rewardRequested);
        result.setRewardActual(rewardActual);
        result.setCreditLogId(creditLogId);
        result.setCreatedAt(c.now);
        settlementResultMapper.insert(result);
    }

    private long insertViolation(Ctx c, long reservationId, AppUser user, ViolationReason reason,
                                 LocalDateTime eventAt) {
        Violation violation = new Violation();
        violation.setReservationId(reservationId);
        violation.setUserId(user.getId());
        violation.setReason(reason);
        violation.setStatus(ViolationStatus.ACTIVE);
        violation.setEventAt(eventAt);
        violation.setProcessedAt(eventAt);
        violation.setNotifiedAt(eventAt);
        violation.setCreatedAt(c.now);
        violationMapper.insert(violation);
        return violation.getId();
    }

    /**
     * Routes demo credit movements through the real credit entry so the log
     * chain stays consistent; also asserts continuity and the 0-100 range.
     * sourceLogId is only set for refunds (points at the original debit log).
     */
    private CreditResult applyCredit(Ctx c, Long userId, Long reservationId, Long violationId,
                                     int requestedDelta, CreditLogType type, String eventKey, String reason) {
        return applyCredit(c, userId, reservationId, violationId, requestedDelta, type, eventKey, reason, null);
    }

    private CreditResult applyCredit(Ctx c, Long userId, Long reservationId, Long violationId,
                                     int requestedDelta, CreditLogType type, String eventKey, String reason,
                                     Long sourceLogId) {
        CreditResult result = creditService.apply(new CreditCommand(eventKey, userId, requestedDelta, type,
                reservationId, violationId, sourceLogId, reason, null));
        Integer expectedBefore = c.expectedScore.get(userId);
        if (expectedBefore != null && result.balanceBefore() != expectedBefore) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "演示信用流水不连续：user=" + userId + " 期望 " + expectedBefore
                            + " 实际 " + result.balanceBefore());
        }
        if (result.balanceBefore() < 0 || result.balanceBefore() > 100
                || result.balanceAfter() < 0 || result.balanceAfter() > 100) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "演示信用余额越界：user=" + userId);
        }
        c.expectedScore.put(userId, result.balanceAfter());
        return result;
    }

    private boolean skipIfDone(Ctx c, Long userId, String demoKey) {
        CommandDedup existing = commandDedupMapper.selectByKey(userId, COMMAND_TYPE, demoKey);
        if (existing != null) {
            c.counters.skipped++;
            log.info("演示夹具跳过：样本已导入 demoKey={}", demoKey);
            return true;
        }
        return false;
    }

    private void recordDedup(Ctx c, Long userId, String demoKey, String resultType, String resultId) {
        CommandDedup dedup = new CommandDedup();
        dedup.setUserId(userId);
        dedup.setCommandType(COMMAND_TYPE);
        dedup.setRequestKey(demoKey);
        dedup.setRequestHash(Ids.sha256Hex(demoKey));
        dedup.setResultType(resultType);
        dedup.setResultId(resultId);
        dedup.setCreatedAt(c.now);
        commandDedupMapper.insert(dedup);
    }

    private void verifyInvariants(Ctx c) {
        for (Long seatId : c.seatsUsed) {
            assertNoOverlap(reservationMapper.selectEffectiveBySeatId(seatId),
                    "座位 id=" + seatId);
        }
        for (Long userId : c.usersUsed) {
            assertNoOverlap(reservationMapper.selectEffectiveByUserId(userId),
                    "用户 id=" + userId);
        }
        for (Map.Entry<Long, Integer> entry : c.expectedScore.entrySet()) {
            CreditAccount account = creditAccountMapper.selectByUserId(entry.getKey());
            if (account == null || account.getScore() == null
                    || !account.getScore().equals(entry.getValue())) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "演示数据不变量校验失败：信用余额不连续 user=" + entry.getKey()
                                + " 期望 " + entry.getValue());
            }
        }
        log.info("演示夹具不变量校验通过：有效预约无重叠、信用余额连续且处于 0—100");
    }

    private void assertNoOverlap(List<Reservation> effective, String scope) {
        for (int i = 0; i < effective.size(); i++) {
            TimeRange a = TimeRange.of(effective.get(i).getStartAt(), effective.get(i).getEndAt());
            for (int j = i + 1; j < effective.size(); j++) {
                TimeRange b = TimeRange.of(effective.get(j).getStartAt(), effective.get(j).getEndAt());
                if (a.overlaps(b)) {
                    throw new BusinessException(ErrorCode.INVALID_STATE,
                            "演示数据不变量校验失败：" + scope + " 存在有效预约重叠");
                }
            }
        }
    }

    private StudyRoom resolveOpenRoom() {
        List<StudyRoom> rooms = studyRoomMapper.selectAll();
        StudyRoom preferred = rooms.stream()
                .filter(room -> "SR-A101".equals(room.getCode()) && room.getStatus() == RoomStatus.OPEN)
                .findFirst()
                .orElse(null);
        if (preferred != null) {
            return preferred;
        }
        return rooms.stream()
                .filter(room -> room.getStatus() == RoomStatus.OPEN)
                .min(Comparator.comparing(StudyRoom::getCode))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE,
                        "未找到开放状态的房间，请先执行 --seed-reference-data 初始化参考数据"));
    }

    private AppUser resolveAdmin() {
        List<AppUser> admins = appUserMapper.selectPage(null, UserRole.ADMIN, null, 0, 1);
        if (admins.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "未找到管理员账号，请先执行 --bootstrap-admin 初始化管理员");
        }
        return admins.get(0);
    }

    private Map<String, AppUser> resolveStudents() {
        Map<String, AppUser> users = new HashMap<>();
        for (String account : STUDENT_ACCOUNTS) {
            AppUser user = appUserMapper.selectByAccount(account);
            if (user == null) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "缺少演示学生账号 " + account + "，请先执行 --seed-reference-data");
            }
            users.put(account, user);
        }
        return users;
    }

    /** Largest grid point not after the value (TimeGrid only offers ceil). */
    private static LocalDateTime floorToGrid(LocalDateTime value, int slotMinutes) {
        return TimeGrid.isOnGrid(value, slotMinutes)
                ? value
                : TimeGrid.ceil(value, slotMinutes).minusMinutes(slotMinutes);
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }
}
