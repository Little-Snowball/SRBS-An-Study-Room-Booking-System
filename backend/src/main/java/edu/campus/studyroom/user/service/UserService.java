package edu.campus.studyroom.user.service;

import edu.campus.studyroom.audit.AuditService;
import edu.campus.studyroom.audit.mapper.AuditLogMapper;
import edu.campus.studyroom.auth.mapper.AuthSessionMapper;
import edu.campus.studyroom.appeal.mapper.AppealMapper;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.IdempotencyService;
import edu.campus.studyroom.common.Ids;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.common.domain.CommandDedup;
import edu.campus.studyroom.common.mapper.CommandDedupMapper;
import edu.campus.studyroom.credit.CreditCommand;
import edu.campus.studyroom.credit.CreditResult;
import edu.campus.studyroom.credit.CreditService;
import edu.campus.studyroom.credit.domain.CreditAccount;
import edu.campus.studyroom.credit.domain.CreditLog;
import edu.campus.studyroom.credit.domain.CreditLogType;
import edu.campus.studyroom.credit.mapper.CreditAccountMapper;
import edu.campus.studyroom.credit.mapper.CreditLogMapper;
import edu.campus.studyroom.file.mapper.FileObjectMapper;
import edu.campus.studyroom.notification.NotificationService;
import edu.campus.studyroom.notification.mapper.NotificationMapper;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.TerminationReason;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.rule.BusinessGuardLock;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import edu.campus.studyroom.security.PasswordPolicy;
import edu.campus.studyroom.settlement.SettlementService;
import edu.campus.studyroom.settlement.mapper.ViolationMapper;
import edu.campus.studyroom.user.domain.AppUser;
import edu.campus.studyroom.user.domain.UserRole;
import edu.campus.studyroom.user.domain.UserStatus;
import edu.campus.studyroom.user.dto.AdminUserItem;
import edu.campus.studyroom.user.dto.CreateStudentRequest;
import edu.campus.studyroom.user.dto.CreateStudentResponse;
import edu.campus.studyroom.user.dto.CreditAdjustRequest;
import edu.campus.studyroom.user.dto.CreditAdjustResponse;
import edu.campus.studyroom.user.dto.OneTimePasswordResponse;
import edu.campus.studyroom.user.dto.ResetPasswordRequest;
import edu.campus.studyroom.user.dto.UpdateStudentRequest;
import edu.campus.studyroom.user.dto.UserBlockRequest;
import edu.campus.studyroom.user.dto.UserStatusRequest;
import edu.campus.studyroom.user.mapper.AppUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 管理员账号管理（手册 7.2 / 12.5）。所有管理写操作遵守同一并发协议：
 * READ_COMMITTED 事务第一行 businessGuardLock.lock() 统一序列化核心写，
 * 然后取一次 clock.nowLocal() 作为唯一可信时间；目标行 selectByIdForUpdate
 * 重读后校验角色（只允许操作学生账号），状态写入使用 auth_version 条件更新，
 * affectedRows=0 一律 409 INVALID_STATE。
 * <p>一次性密码仅随创建/重置响应下发一次：不写日志、不落库明文、同键重放
 * 不二次改密也不返回明文。禁用/拉黑的有效订单走 SettlementService.adminTerminate
 * 无责终止（零奖惩），与撤销会话、通知学生同事务。
 */
@Service
public class UserService {

    /** 一次性密码字符类：剔除易混淆字符（I/O/l/0/1）。 */
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final int ONE_TIME_PASSWORD_LENGTH = 12;

    /** 新学生初始信用分（schema 满分，结构性常量而非可配置规则）。 */
    private static final int INITIAL_CREDIT = 100;

    private static final String CMD_RESET_PASSWORD = "USER_RESET_PASSWORD";
    private static final String CMD_CREDIT_ADJUST = "USER_CREDIT_ADJUST";

    private final BusinessGuardLock businessGuardLock;
    private final BusinessClock clock;
    private final PasswordPolicy passwordPolicy;
    private final AppUserMapper appUserMapper;
    private final CreditAccountMapper creditAccountMapper;
    private final CreditLogMapper creditLogMapper;
    private final CreditService creditService;
    private final ReservationMapper reservationMapper;
    private final AuthSessionMapper authSessionMapper;
    private final NotificationService notificationService;
    private final SettlementService settlementService;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final CommandDedupMapper commandDedupMapper;
    private final ViolationMapper violationMapper;
    private final AppealMapper appealMapper;
    private final NotificationMapper notificationMapper;
    private final FileObjectMapper fileObjectMapper;
    private final AuditLogMapper auditLogMapper;

    private final SecureRandom random = new SecureRandom();

    public UserService(BusinessGuardLock businessGuardLock, BusinessClock clock, PasswordPolicy passwordPolicy,
                       AppUserMapper appUserMapper, CreditAccountMapper creditAccountMapper,
                       CreditLogMapper creditLogMapper, CreditService creditService,
                       ReservationMapper reservationMapper, AuthSessionMapper authSessionMapper,
                       NotificationService notificationService, SettlementService settlementService,
                       AuditService auditService, IdempotencyService idempotencyService,
                       CommandDedupMapper commandDedupMapper, ViolationMapper violationMapper,
                       AppealMapper appealMapper, NotificationMapper notificationMapper,
                       FileObjectMapper fileObjectMapper, AuditLogMapper auditLogMapper) {
        this.businessGuardLock = businessGuardLock;
        this.clock = clock;
        this.passwordPolicy = passwordPolicy;
        this.appUserMapper = appUserMapper;
        this.creditAccountMapper = creditAccountMapper;
        this.creditLogMapper = creditLogMapper;
        this.creditService = creditService;
        this.reservationMapper = reservationMapper;
        this.authSessionMapper = authSessionMapper;
        this.notificationService = notificationService;
        this.settlementService = settlementService;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
        this.commandDedupMapper = commandDedupMapper;
        this.violationMapper = violationMapper;
        this.appealMapper = appealMapper;
        this.notificationMapper = notificationMapper;
        this.fileObjectMapper = fileObjectMapper;
        this.auditLogMapper = auditLogMapper;
    }

    // ------------------------------------------------------------ 查询

    /** Admin user paging: account prefix match plus optional role/status filters. */
    public PageResult<AdminUserItem> pageUsers(String account, UserRole role, UserStatus status,
                                               PageQuery query) {
        String accountFilter = account == null || account.isBlank() ? null : account.trim();
        List<AppUser> users = appUserMapper.selectPage(accountFilter, role, status,
                query.offset(), query.size());
        long total = appUserMapper.countPage(accountFilter, role, status);
        List<AdminUserItem> items = users.stream().map(this::toAdminItem).toList();
        return PageResult.of(items, total, query.page(), query.size());
    }

    // ------------------------------------------------------------ 创建学生

    /**
     * 创建学生账号。学号 trim 后大写并查重（409 SLOT_CONFLICT）；服务端生成一次性密码，
     * hash 后入库；同步创建满分信用账户。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreateStudentResponse createStudent(CurrentUserPrincipal admin, CreateStudentRequest request) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();

        String account = request.account().trim().toUpperCase();
        if (appUserMapper.selectByAccount(account) != null) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "学号已存在");
        }
        String oneTimePassword = generateOneTimePassword();

        AppUser user = new AppUser();
        user.setAccount(account);
        user.setPasswordHash(passwordPolicy.hash(oneTimePassword));
        user.setName(request.name().trim());
        user.setCollege(request.college() == null ? null : request.college().trim());
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setBlocked(false);
        user.setMustChangePassword(false);
        user.setAuthVersion(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        appUserMapper.insert(user);

        CreditAccount credit = new CreditAccount();
        credit.setUserId(user.getId());
        credit.setScore(INITIAL_CREDIT);
        credit.setVersion(0L);
        credit.setUpdatedAt(now);
        creditAccountMapper.insert(credit);

        auditService.record(admin, "USER_CREATE_STUDENT", "USER", String.valueOf(user.getId()),
                "{\"account\":\"" + jsonEscape(account) + "\",\"role\":\"STUDENT\"}");
        // 一次性密码仅随本次响应下发：不写审计、不落库明文
        return new CreateStudentResponse(toAdminItem(user), oneTimePassword);
    }

    // ------------------------------------------------------------ 修改学生信息

    /**
     * 修改学生身份信息（学号/姓名/学院）。学号 trim 大写查重（409 SLOT_CONFLICT）；
     * 学号变更时撤销该学生全部会话（身份变化即时失效）。行锁 + guard 序列化。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminUserItem updateStudent(CurrentUserPrincipal admin, Long userId, UpdateStudentRequest request) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();

        AppUser target = requireStudentTarget(admin, userId);

        String account = request.account().trim().toUpperCase();
        String name = request.name().trim();
        if (account.isEmpty()) {
            throw BusinessException.validation("学号不能为空");
        }
        if (name.isEmpty()) {
            throw BusinessException.validation("姓名不能为空");
        }
        AppUser duplicate = appUserMapper.selectByAccount(account);
        if (duplicate != null && !duplicate.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "学号已存在");
        }
        String college = request.college() == null || request.college().isBlank()
                ? null : request.college().trim();

        boolean accountChanged = !target.getAccount().equals(account);
        appUserMapper.updateIdentity(userId, account, name, college, now);
        if (accountChanged) {
            authSessionMapper.revokeAllForUser(userId, now);
        }

        auditService.record(admin, "USER_UPDATE_IDENTITY", "USER", String.valueOf(userId),
                "{\"account\":\"" + jsonEscape(account) + "\",\"accountChanged\":"
                        + accountChanged + ",\"name\":\"" + jsonEscape(name) + "\"}");
        return toAdminItem(appUserMapper.selectById(userId));
    }

    // ------------------------------------------------------------ 删除学生账号

    /**
     * 删除学生账号（历史不删铁律的守门实现）：存在预约/违约/信用流水/申诉任一
     * 历史即 409 拒绝（提示改用「禁用」）。无历史时同事务清理会话、通知、幂等
     * 记录、上传文件、信用账户，并将审计日志的操作人置空以保留审计历史。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteStudent(CurrentUserPrincipal admin, Long userId) {
        businessGuardLock.lock();

        AppUser target = requireStudentTarget(admin, userId);

        long reservations = reservationMapper.countByUserId(userId, null, null, null);
        long violations = violationMapper.countByUserId(userId, null);
        long creditLogs = creditLogMapper.countByUserId(userId, null);
        long appeals = appealMapper.countByUserId(userId, null);
        if (reservations > 0 || violations > 0 || creditLogs > 0 || appeals > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "该学生存在业务历史（预约 " + reservations + "、违约 " + violations
                            + "、信用流水 " + creditLogs + "、申诉 " + appeals
                            + " 条），不可删除；历史记录必须保留，请改用「禁用」");
        }

        authSessionMapper.deleteByUserId(userId);
        notificationMapper.deleteByUserId(userId);
        commandDedupMapper.deleteByUserId(userId);
        fileObjectMapper.deleteByOwner(userId);
        creditAccountMapper.deleteByUserId(userId);
        auditLogMapper.clearActor(userId);
        appUserMapper.deleteById(userId);

        auditService.record(admin, "USER_DELETE", "USER", String.valueOf(userId),
                "{\"account\":\"" + jsonEscape(target.getAccount()) + "\",\"name\":\""
                        + jsonEscape(target.getName()) + "\"}");
    }

    // ------------------------------------------------------------ 启用/禁用

    /**
     * 启用/禁用学生账号。expectedAuthVersion 条件更新，不符返回 409。
     * 禁用时若有有效订单：未确认（confirmTerminate=false）抛 422 并说明笔数；
     * 确认后逐笔无责终止（RESERVED→CANCELLED，IN_USE/LEFT_TEMP→COMPLETED 零奖惩）、
     * 撤销全部会话并通知学生，全部与状态写入同事务。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminUserItem setUserStatus(CurrentUserPrincipal admin, Long userId, UserStatusRequest request) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();

        AppUser target = requireStudentTarget(admin, userId);
        String reason = normalizeReason(request.reason());

        // guard 锁持有期间无其他写者可产生新订单，一次读取对确认与终止同时有效
        List<Reservation> effective = List.of();
        if (request.status() == UserStatus.DISABLED) {
            effective = reservationMapper.selectEffectiveByUserId(userId);
            if (!effective.isEmpty() && !request.confirmTerminate()) {
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED,
                        "该账号有 " + effective.size() + " 笔有效订单，需确认无责终止");
            }
        }

        int affected = appUserMapper.updateStatus(userId, request.expectedAuthVersion(),
                request.status(), Boolean.TRUE.equals(target.isBlocked()), now);
        if (affected != 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "账号状态已变化，请刷新");
        }

        if (request.status() == UserStatus.DISABLED) {
            disableSideEffects(userId, effective, reason, now);
        }

        auditService.record(admin, "USER_STATUS_UPDATE", "USER", String.valueOf(userId),
                "{\"status\":\"" + request.status() + "\",\"expectedAuthVersion\":"
                        + request.expectedAuthVersion() + ",\"confirmTerminate\":"
                        + request.confirmTerminate() + ",\"reason\":\""
                        + jsonEscape(reason) + "\"}");
        return toAdminItem(appUserMapper.selectById(userId));
    }

    // ------------------------------------------------------------ 人工黑名单

    /**
     * 人工黑名单开关。blocked=true 与禁用同样无责处理有效订单、撤销会话并通知；
     * blocked=false 仅解除人工封禁——不绕过信用门槛，预约时的信用校验仍按
     * 当前生效规则的 minCredit 执行（注释说明：解除黑名单≠恢复预约资格）。
     * expectedAuthVersion 取当前行读值，条件更新防丢失更新。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminUserItem setUserBlocked(CurrentUserPrincipal admin, Long userId, UserBlockRequest request) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();

        AppUser target = requireStudentTarget(admin, userId);
        String reason = normalizeReason(request.reason());

        int affected = appUserMapper.updateStatus(userId, target.getAuthVersion(),
                target.getStatus(), request.blocked(), now);
        if (affected != 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "账号状态已变化，请刷新");
        }

        if (request.blocked()) {
            blockSideEffects(userId,
                    reservationMapper.selectEffectiveByUserId(userId), reason, now);
        }

        auditService.record(admin, request.blocked() ? "USER_BLOCK" : "USER_UNBLOCK", "USER",
                String.valueOf(userId),
                "{\"blocked\":" + request.blocked() + ",\"reason\":\""
                        + jsonEscape(reason) + "\"}");
        return toAdminItem(appUserMapper.selectById(userId));
    }

    // ------------------------------------------------------------ 重置密码

    /**
     * 重置学生密码（幂等）。同键重放不二次改密、不返回明文，仅回报
     * credentialAlreadyIssued=true；同键不同参数（含目标用户）抛
     * IDEMPOTENCY_KEY_REUSED。首次重置生成一次性密码，
     * auth_version bump + 撤销全部会话，旧一次性密码立即失效。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OneTimePasswordResponse resetPassword(CurrentUserPrincipal admin, Long userId,
                                                 ResetPasswordRequest request, String idempotencyKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();

        requireStudentTarget(admin, userId);

        // 请求摘要覆盖目标用户与请求体：同键换目标/换参数都视为键复用
        String requestHash = Ids.sha256Hex(userId + "|" + String.valueOf(request));
        CommandDedup existing =
                commandDedupMapper.selectByKey(admin.userId(), CMD_RESET_PASSWORD, idempotencyKey);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "幂等键已被不同参数的请求使用，请更换请求键");
            }
            return new OneTimePasswordResponse(String.valueOf(userId), null, true);
        }

        String oneTimePassword = generateOneTimePassword();
        String passwordHash = passwordPolicy.hash(oneTimePassword);
        appUserMapper.updatePassword(userId, passwordHash, false, now);
        authSessionMapper.revokeAllForUser(userId, now);
        idempotencyService.recordSuccess(admin.userId(), CMD_RESET_PASSWORD, idempotencyKey,
                requestHash, "USER", String.valueOf(userId));
        auditService.record(admin, "USER_RESET_PASSWORD", "USER", String.valueOf(userId),
                "{\"mustChangePassword\":false,\"reason\":\"" + jsonEscape(request.reason()) + "\"}");
        return new OneTimePasswordResponse(String.valueOf(userId), oneTimePassword, false);
    }

    // ------------------------------------------------------------ 人工信用调整

    /**
     * 人工信用调整（幂等）。requestedDelta = targetScore - before，唯一入口是
     * CreditService.apply（钳制 0—100、写流水、审计 operator）；apply 的 MANDATORY
     * 事务语义由本方法的 @Transactional 承载。同键重放按原流水值重放结果。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreditAdjustResponse adjustCredit(CurrentUserPrincipal admin, Long userId,
                                             CreditAdjustRequest request, String idempotencyKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();

        requireStudentTarget(admin, userId);

        String requestHash = Ids.sha256Hex(userId + "|" + String.valueOf(request));
        CommandDedup existing =
                commandDedupMapper.selectByKey(admin.userId(), CMD_CREDIT_ADJUST, idempotencyKey);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "幂等键已被不同参数的请求使用，请更换请求键");
            }
            return replayAdjustResponse(existing.getResultId());
        }

        CreditAccount account = creditAccountMapper.selectByUserIdForUpdate(userId);
        if (account == null || account.getScore() == null) {
            throw BusinessException.internal("信用账户缺失，用户 id=" + userId);
        }
        int before = account.getScore();
        String reason = normalizeReason(request.reason());
        CreditResult result = creditService.apply(new CreditCommand(
                "manual:" + UUID.randomUUID(),
                userId,
                request.targetScore() - before,
                CreditLogType.MANUAL_ADJUST,
                null,
                null,
                null,
                reason,
                admin.userId()));

        idempotencyService.recordSuccess(admin.userId(), CMD_CREDIT_ADJUST, idempotencyKey,
                requestHash, "USER", String.valueOf(result.logId()));
        auditService.record(admin, "USER_CREDIT_ADJUST", "USER", String.valueOf(userId),
                "{\"targetScore\":" + request.targetScore() + ",\"requestedDelta\":"
                        + (request.targetScore() - before) + ",\"balanceBefore\":" + result.balanceBefore()
                        + ",\"balanceAfter\":" + result.balanceAfter() + ",\"logId\":"
                        + result.logId() + ",\"reason\":\"" + jsonEscape(reason) + "\"}");
        return new CreditAdjustResponse(result.balanceBefore(), result.balanceAfter(),
                String.valueOf(result.logId()));
    }

    // ------------------------------------------------------------ 内部实现

    /**
     * 目标必须是学生账号：不存在 404；管理员账号 403（含对自己操作）；
     * 其他角色理论上不存在，防御性 404。行锁随读随取（guard 事务内）。
     */
    private AppUser requireStudentTarget(CurrentUserPrincipal admin, Long userId) {
        if (admin.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能对自己执行该管理操作");
        }
        AppUser target = appUserMapper.selectByIdForUpdate(userId);
        if (target == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (target.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能操作其他管理员账号");
        }
        if (target.getRole() != UserRole.STUDENT) {
            throw BusinessException.notFound("目标账号不是学生");
        }
        return target;
    }

    /** 禁用副作用：有效订单无责终止 + 撤销会话 + 通知学生（与状态写入同事务）。 */
    private void disableSideEffects(Long userId, List<Reservation> effective, String reason,
                                    LocalDateTime now) {
        for (Reservation reservation : effective) {
            settlementService.adminTerminate(reservation.getId(), now, TerminationReason.ACCOUNT_DISABLED);
        }
        authSessionMapper.revokeAllForUser(userId, now);
        notificationService.publish(userId,
                "user:" + userId + ":disabled",
                "ACCOUNT",
                "账号禁用通知",
                "您的账号已被管理员禁用，原因：" + reason + "。有效订单已无责终止，禁用期间无法登录与新建业务。",
                null,
                null,
                now);
    }

    /** 拉黑副作用：与禁用一致的无责终止 + 撤销会话 + 通知（同事务）。 */
    private void blockSideEffects(Long userId, List<Reservation> effective, String reason,
                                  LocalDateTime now) {
        for (Reservation reservation : effective) {
            settlementService.adminTerminate(reservation.getId(), now, TerminationReason.ACCOUNT_DISABLED);
        }
        authSessionMapper.revokeAllForUser(userId, now);
        notificationService.publish(userId,
                "user:" + userId + ":blocked",
                "ACCOUNT",
                "账号封禁通知",
                "您的账号已被管理员列入黑名单，原因：" + reason + "。有效订单已无责终止，解除前无法登录与新建业务。",
                null,
                null,
                now);
    }

    /** 幂等重放：按记录的 credit_log 原值重组调整结果（dedup 与流水同事务提交）。 */
    private CreditAdjustResponse replayAdjustResponse(String resultId) {
        CreditLog log = null;
        if (resultId != null) {
            try {
                log = creditLogMapper.selectById(Long.parseLong(resultId));
            } catch (NumberFormatException ignored) {
                log = null;
            }
        }
        if (log == null) {
            throw BusinessException.internal("幂等重放记录缺失，请更换请求键重试");
        }
        return new CreditAdjustResponse(
                log.getBalanceBefore() == null ? 0 : log.getBalanceBefore(),
                log.getBalanceAfter() == null ? 0 : log.getBalanceAfter(),
                String.valueOf(log.getId()));
    }

    private AdminUserItem toAdminItem(AppUser user) {
        CreditAccount credit = creditAccountMapper.selectByUserId(user.getId());
        int score = credit == null || credit.getScore() == null ? INITIAL_CREDIT : credit.getScore();
        return new AdminUserItem(
                String.valueOf(user.getId()),
                user.getAccount(),
                user.getName(),
                user.getCollege(),
                user.getRole(),
                user.getStatus(),
                Boolean.TRUE.equals(user.isBlocked()),
                score,
                user.getAuthVersion() == null ? 0 : user.getAuthVersion(),
                clock.toOffset(user.getCreatedAt()));
    }

    /**
     * 12 位随机一次性密码：至少各含一位大写字母、小写字母、数字，其余从全字符集
     * 抽取后 Fisher-Yates 洗牌；SecureRandom 保证不可预测。
     */
    private String generateOneTimePassword() {
        String alphabet = UPPER + LOWER + DIGITS;
        char[] chars = new char[ONE_TIME_PASSWORD_LENGTH];
        chars[0] = UPPER.charAt(random.nextInt(UPPER.length()));
        chars[1] = LOWER.charAt(random.nextInt(LOWER.length()));
        chars[2] = DIGITS.charAt(random.nextInt(DIGITS.length()));
        for (int i = 3; i < ONE_TIME_PASSWORD_LENGTH; i++) {
            chars[i] = alphabet.charAt(random.nextInt(alphabet.length()));
        }
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    /** 操作原因统一归一化：null/空串均按空字符串入库（credit_log.reason NOT NULL）。 */
    private static String normalizeReason(String reason) {
        return reason == null ? "" : reason.trim();
    }

    /** 审计摘要 JSON 转义：引号/反斜杠/换行转义，其余控制字符替换为空格，保证 JSON 列合法。 */
    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c < 0x20 ? ' ' : c);
            }
        }
        return sb.toString();
    }
}
