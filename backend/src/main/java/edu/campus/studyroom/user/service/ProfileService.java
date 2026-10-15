package edu.campus.studyroom.user.service;

import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.credit.domain.CreditAccount;
import edu.campus.studyroom.credit.domain.CreditLog;
import edu.campus.studyroom.credit.domain.CreditLogType;
import edu.campus.studyroom.credit.mapper.CreditAccountMapper;
import edu.campus.studyroom.credit.mapper.CreditLogMapper;
import edu.campus.studyroom.appeal.domain.Appeal;
import edu.campus.studyroom.appeal.domain.AppealStatus;
import edu.campus.studyroom.appeal.mapper.AppealMapper;
import edu.campus.studyroom.credit.domain.CreditLog;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.TerminationReason;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.rule.RuleService;
import edu.campus.studyroom.settlement.domain.Violation;
import edu.campus.studyroom.settlement.domain.ViolationStatus;
import edu.campus.studyroom.settlement.mapper.ViolationMapper;
import edu.campus.studyroom.user.dto.ViolationItem;
import edu.campus.studyroom.user.domain.AppUser;
import edu.campus.studyroom.user.dto.CreditLogItem;
import edu.campus.studyroom.user.dto.UserProfile;
import edu.campus.studyroom.user.mapper.AppUserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 个人中心查询：我的资料与我的信用流水。只读、不走 guard 事务。
 * creditRestricted 由当前生效规则的 minCredit 派生（禁止写死门槛）；
 * 信用账户缺失时按 schema 满分返回，查询视图不因缺行报错。
 */
@Service
public class ProfileService {

    /** 账户缺失时的防御性满分（schema CK 上界，结构性常量）。 */
    private static final int FULL_CREDIT = 100;

    private final AppUserMapper appUserMapper;
    private final CreditAccountMapper creditAccountMapper;
    private final CreditLogMapper creditLogMapper;
    private final RuleService ruleService;
    private final BusinessClock clock;
    private final ViolationMapper violationMapper;
    private final AppealMapper appealMapper;
    private final ReservationMapper reservationMapper;

    /** 申诉窗口：违约通知可得知时刻起 48 小时（含截止点）。 */
    private static final long APPEAL_WINDOW_HOURS = 48L;

    public ProfileService(AppUserMapper appUserMapper, CreditAccountMapper creditAccountMapper,
                          CreditLogMapper creditLogMapper, RuleService ruleService, BusinessClock clock,
                          ViolationMapper violationMapper, AppealMapper appealMapper,
                          ReservationMapper reservationMapper) {
        this.appUserMapper = appUserMapper;
        this.creditAccountMapper = creditAccountMapper;
        this.creditLogMapper = creditLogMapper;
        this.ruleService = ruleService;
        this.clock = clock;
        this.violationMapper = violationMapper;
        this.appealMapper = appealMapper;
        this.reservationMapper = reservationMapper;
    }

    /**
     * 我的处罚记录。deducted 取该订单扣分流水的实际入账绝对值（同单多流水时取违约类型合计）；
     * appealable = 仍 ACTIVE 且在 48h 窗口内且无待审工单（仅提示，提交时服务端重判）。
     */
    public PageResult<ViolationItem> myViolations(Long userId, ViolationStatus statusFilter, PageQuery query) {
        List<Violation> rows = violationMapper.selectByUserIdPage(userId, statusFilter,
                query.offset(), query.size());
        long total = violationMapper.countByUserId(userId, statusFilter);
        java.time.LocalDateTime now = clock.nowLocal();
        List<ViolationItem> items = new java.util.ArrayList<>(rows.size());
        for (Violation v : rows) {
            Reservation order = reservationMapper.selectById(v.getReservationId());
            int deducted = deductedFor(v);
            boolean appealable = v.getStatus() == ViolationStatus.ACTIVE
                    && v.getNotifiedAt() != null
                    && !now.isAfter(v.getNotifiedAt().plusHours(APPEAL_WINDOW_HOURS))
                    && !hasPendingAppeal(v.getId());
            items.add(new ViolationItem(String.valueOf(v.getId()), String.valueOf(v.getReservationId()),
                    v.getReason().name(), v.getStatus().name(),
                    clock.toOffset(v.getEventAt()), clock.toOffset(v.getNotifiedAt()),
                    deducted, appealable,
                    order == null ? null : order.getOrderNo(),
                    order == null ? null : order.getSeatCodeSnapshot(),
                    order == null ? null : order.getRoomNameSnapshot()));
        }
        return PageResult.of(items, total, query.page(), query.size());
    }

    private int deductedFor(Violation violation) {
        int total = 0;
        for (CreditLog log : creditLogMapper.selectByReservationId(violation.getReservationId())) {
            if (log.getActualDelta() != null && log.getActualDelta() < 0) {
                total += -log.getActualDelta();
            }
        }
        return total;
    }

    private boolean hasPendingAppeal(Long violationId) {
        Appeal appeal = appealMapper.selectByViolationId(violationId);
        return appeal != null && appeal.getStatus() == AppealStatus.PENDING;
    }

    /** 我的资料：身份、当前信用分、是否低于预约门槛、人工黑名单标记。 */
    public UserProfile myProfile(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        int score = currentCreditScore(userId);
        // 预约门槛来自当前生效规则，任何调用方不得写死数值
        boolean creditRestricted = score < ruleService.activeRule().minCredit();
        return new UserProfile(
                String.valueOf(user.getId()),
                user.getAccount(),
                user.getName(),
                user.getCollege(),
                user.getRole(),
                score,
                creditRestricted,
                Boolean.TRUE.equals(user.isBlocked()));
    }

    /** 我的信用流水：可按类型过滤，按时间倒序分页。 */
    public PageResult<CreditLogItem> myCreditLogs(Long userId, CreditLogType type, PageQuery query) {
        List<CreditLog> logs =
                creditLogMapper.selectByUserIdPage(userId, type, query.offset(), query.size());
        long total = creditLogMapper.countByUserId(userId, type);
        List<CreditLogItem> items = logs.stream().map(this::toItem).toList();
        return PageResult.of(items, total, query.page(), query.size());
    }

    private int currentCreditScore(Long userId) {
        CreditAccount account = creditAccountMapper.selectByUserId(userId);
        return account == null || account.getScore() == null ? FULL_CREDIT : account.getScore();
    }

    private CreditLogItem toItem(CreditLog log) {
        return new CreditLogItem(
                String.valueOf(log.getId()),
                log.getType(),
                nullToZero(log.getRequestedDelta()),
                nullToZero(log.getActualDelta()),
                nullToZero(log.getCappedDelta()),
                nullToZero(log.getBalanceBefore()),
                nullToZero(log.getBalanceAfter()),
                log.getReason(),
                log.getReservationId() == null ? null : String.valueOf(log.getReservationId()),
                log.getViolationId() == null ? null : String.valueOf(log.getViolationId()),
                clock.toOffset(log.getCreatedAt()));
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
