package edu.campus.studyroom.auth.service;

import edu.campus.studyroom.auth.domain.AuthSession;
import edu.campus.studyroom.auth.dto.LoginResponse;
import edu.campus.studyroom.auth.dto.UserProfile;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.credit.domain.CreditAccount;
import edu.campus.studyroom.credit.mapper.CreditAccountMapper;
import edu.campus.studyroom.rule.RuleService;
import edu.campus.studyroom.security.JwtTokenService;
import edu.campus.studyroom.security.PasswordPolicy;
import edu.campus.studyroom.security.RateLimiter;
import edu.campus.studyroom.user.domain.AppUser;
import edu.campus.studyroom.user.domain.UserStatus;
import edu.campus.studyroom.user.mapper.AppUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication commands. Credential verification runs outside any
 * transaction (BCrypt is slow); the guarded session transaction then re-reads
 * the user row so stale credentials or versions cannot mint a session.
 * Login failures share one message so account existence is never leaked.
 */
@Service
public class AuthService {

    /** Used when the user has no credit_account row yet. */
    static final int DEFAULT_CREDIT_SCORE = 100;

    private final AppUserMapper appUserMapper;
    private final CreditAccountMapper creditAccountMapper;
    private final SessionService sessionService;
    private final PasswordPolicy passwordPolicy;
    private final RateLimiter rateLimiter;
    private final JwtTokenService jwtTokenService;
    private final RuleService ruleService;
    private final BusinessClock clock;

    public AuthService(AppUserMapper appUserMapper, CreditAccountMapper creditAccountMapper,
                       SessionService sessionService,
                       PasswordPolicy passwordPolicy, RateLimiter rateLimiter,
                       JwtTokenService jwtTokenService, RuleService ruleService,
                       BusinessClock clock) {
        this.appUserMapper = appUserMapper;
        this.creditAccountMapper = creditAccountMapper;
        this.sessionService = sessionService;
        this.passwordPolicy = passwordPolicy;
        this.rateLimiter = rateLimiter;
        this.jwtTokenService = jwtTokenService;
        this.ruleService = ruleService;
        this.clock = clock;
    }

    public LoginResponse login(String account, String password, String ip) {
        String normalizedAccount = account == null ? "" : account.trim();
        rateLimiter.checkLoginAllowed(ip, normalizedAccount);
        rateLimiter.recordLoginAttempt(ip);
        AppUser user = appUserMapper.selectByAccount(normalizedAccount);
        if (user == null || !passwordPolicy.matches(password, user.getPasswordHash())) {
            rateLimiter.recordLoginFailure(normalizedAccount);
            throw new BusinessException(ErrorCode.AUTH_REQUIRED, "学号或密码错误");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号已禁用，请联系管理员");
        }
        if (Boolean.TRUE.equals(user.isBlocked())) {
            throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED, "账号已被拉黑，暂无法登录");
        }
        AuthSession session = sessionService.createSession(user, clock);
        String token = jwtTokenService.issue(user.getId(), session.getAuthVersion(),
                session.getId(), clock.now());
        UserProfile profile = buildProfile(user);
        return new LoginResponse(token, clock.toOffset(session.getExpiresAt()), profile);
    }

    public void logout(String sid) {
        sessionService.revokeSession(sid);
    }

    /**
     * Changes the caller's password: bumps auth_version (invalidating every
     * issued token) and revokes all active sessions in the same transaction.
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordPolicy.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "原密码不正确");
        }
        passwordPolicy.validate(newPassword, user.getAccount(), user.getName());
        String hash = passwordPolicy.hash(newPassword);
        appUserMapper.updatePassword(userId, hash, false, clock.nowLocal());
        sessionService.revokeAllSessions(userId);
    }

    private UserProfile buildProfile(AppUser user) {
        CreditAccount creditAccount = creditAccountMapper.selectByUserId(user.getId());
        int creditScore = creditAccount == null || creditAccount.getScore() == null
                ? DEFAULT_CREDIT_SCORE : creditAccount.getScore();
        int minCredit = ruleService.activeRule().minCredit();
        return new UserProfile(
                String.valueOf(user.getId()),
                user.getAccount(),
                user.getName(),
                user.getCollege(),
                user.getRole().name(),
                creditScore,
                creditScore < minCredit,
                Boolean.TRUE.equals(user.isBlocked()));
    }
}
