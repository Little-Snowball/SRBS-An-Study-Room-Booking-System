package edu.campus.studyroom.auth.service;

import edu.campus.studyroom.auth.domain.AuthSession;
import edu.campus.studyroom.auth.mapper.AuthSessionMapper;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.rule.BusinessGuardLock;
import edu.campus.studyroom.security.AppProperties;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import edu.campus.studyroom.user.domain.AppUser;
import edu.campus.studyroom.user.domain.UserStatus;
import edu.campus.studyroom.user.mapper.AppUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Auth session lifecycle. Session creation is a short guarded transaction:
 * guard row lock + row-locked user re-read, so a password change or status
 * change that happened after the (transaction-less) credential check can never
 * produce a valid new session. At most maxSessionsPerUser active sessions per
 * account; the oldest ones are revoked first.
 */
@Service
public class SessionService {

    private final BusinessGuardLock businessGuardLock;
    private final AppUserMapper appUserMapper;
    private final AuthSessionMapper authSessionMapper;
    private final AppProperties appProperties;
    private final BusinessClock clock;

    public SessionService(BusinessGuardLock businessGuardLock, AppUserMapper appUserMapper,
                          AuthSessionMapper authSessionMapper, AppProperties appProperties,
                          BusinessClock clock) {
        this.businessGuardLock = businessGuardLock;
        this.appUserMapper = appUserMapper;
        this.authSessionMapper = authSessionMapper;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    /**
     * Creates an active session for the user and returns it (id + expiry).
     * Rejects with INVALID_STATE when the user row changed after the caller's
     * credential check (disabled, blocked or bumped auth_version).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthSession createSession(AppUser user, BusinessClock clock) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        AppUser fresh = appUserMapper.selectByIdForUpdate(user.getId());
        if (fresh == null
                || fresh.getStatus() != UserStatus.ACTIVE
                || Boolean.TRUE.equals(fresh.isBlocked())
                || !Objects.equals(fresh.getAuthVersion(), user.getAuthVersion())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "登录状态已变化，请重试");
        }
        evictOldestSessions(fresh.getId(), now);
        AuthSession session = new AuthSession();
        session.setId(CurrentUserPrincipal.newSessionId());
        session.setUserId(fresh.getId());
        session.setAuthVersion(fresh.getAuthVersion() == null ? 0 : fresh.getAuthVersion());
        session.setExpiresAt(CurrentUserPrincipal.expiryFrom(clock,
                appProperties.getSession().getAccessTokenHours()));
        session.setCreatedAt(now);
        authSessionMapper.insert(session);
        return session;
    }

    /** Revokes a single session; repeated calls are no-ops. */
    @Transactional
    public void revokeSession(String sid) {
        if (sid == null || sid.isBlank()) {
            return;
        }
        authSessionMapper.revoke(sid, clock.nowLocal());
    }

    /** Revokes every active session of the user (logout-everywhere). */
    @Transactional
    public void revokeAllSessions(Long userId) {
        if (userId == null) {
            return;
        }
        authSessionMapper.revokeAllForUser(userId, clock.nowLocal());
    }

    private void evictOldestSessions(Long userId, LocalDateTime now) {
        int maxSessions = appProperties.getSession().getMaxSessionsPerUser();
        long active = authSessionMapper.countActiveForUser(userId, now);
        while (active >= maxSessions) {
            List<AuthSession> oldest = authSessionMapper.selectOldestActive(userId, now, 1);
            if (oldest.isEmpty()) {
                break;
            }
            authSessionMapper.revoke(oldest.get(0).getId(), now);
            active--;
        }
    }
}
