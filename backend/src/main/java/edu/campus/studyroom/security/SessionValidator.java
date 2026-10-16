package edu.campus.studyroom.security;

import edu.campus.studyroom.user.domain.AppUser;
import edu.campus.studyroom.user.domain.UserStatus;
import edu.campus.studyroom.user.mapper.AppUserMapper;
import edu.campus.studyroom.auth.domain.AuthSession;
import edu.campus.studyroom.auth.mapper.AuthSessionMapper;
import edu.campus.studyroom.common.BusinessClock;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Per-request session validation. Every authenticated request hits the database
 * on purpose: logout and disable take effect immediately, at the cost of statelessness.
 */
@Service
public class SessionValidator {

    private final AuthSessionMapper sessionMapper;
    private final AppUserMapper userMapper;
    private final BusinessClock clock;

    public SessionValidator(AuthSessionMapper sessionMapper, AppUserMapper userMapper, BusinessClock clock) {
        this.sessionMapper = sessionMapper;
        this.userMapper = userMapper;
        this.clock = clock;
    }

    /**
     * Validates the session referenced by the token.
     *
     * @return the current user when the session is usable, null otherwise
     */
    public CurrentUserPrincipal validate(String sid, int authVersion) {
        AuthSession session = sessionMapper.selectById(sid);
        LocalDateTime now = clock.nowLocal();
        if (session == null || session.getRevokedAt() != null
                || session.getExpiresAt() == null || session.getExpiresAt().isBefore(now)) {
            return null;
        }
        if (!Integer.valueOf(authVersion).equals(session.getAuthVersion())) {
            return null;
        }
        AppUser user = userMapper.selectById(session.getUserId());
        if (user == null || user.getStatus() != UserStatus.ACTIVE
                || Boolean.TRUE.equals(user.isBlocked())
                || !Integer.valueOf(authVersion).equals(user.getAuthVersion())) {
            return null;
        }
        return new CurrentUserPrincipal(user.getId(), user.getAccount(), user.getName(), user.getRole(),
                user.getStatus(), Boolean.TRUE.equals(user.isBlocked()));
    }
}
