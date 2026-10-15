package edu.campus.studyroom.security;

import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.BusinessException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiting for the single-instance course deployment.
 * Counters reset on restart; this limitation is documented in the README.
 * Login limits: per IP 20/min, per account 5 failures per 15 min then a 15 min pause.
 * Check-in limits: per user+order 5 errors per 5 min, per IP 30/min.
 */
@Component
public class RateLimiter {

    private final AppProperties properties;

    private final Map<String, Deque<Long>> loginIpAttempts = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> loginAccountFailures = new ConcurrentHashMap<>();
    private final Map<String, Long> loginPausedUntil = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> checkinOrderErrors = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> checkinIpAttempts = new ConcurrentHashMap<>();

    public RateLimiter(AppProperties properties) {
        this.properties = properties;
    }

    // ---------- login ----------

    public void checkLoginAllowed(String ip, String account) {
        long now = Instant.now().toEpochMilli();
        Long pausedUntil = loginPausedUntil.get(account);
        if (pausedUntil != null) {
            if (now < pausedUntil) {
                long retryAfterSeconds = Math.max(1, (pausedUntil - now) / 1000);
                throw new BusinessException(ErrorCode.RATE_LIMITED,
                        "失败次数过多，账号暂停登录", retryAfterSeconds);
            }
            loginPausedUntil.remove(account);
            loginAccountFailures.remove(account);
        }
        if (countWithin(loginIpAttempts, ip, now, 60_000) >= properties.getRateLimit().getLoginPerIpPerMinute()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "登录尝试过于频繁", 60);
        }
    }

    public void recordLoginFailure(String account) {
        long now = Instant.now().toEpochMilli();
        Deque<Long> deque = loginAccountFailures.computeIfAbsent(account, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(now);
            while (!deque.isEmpty() && deque.peekFirst() < now - 900_000) {
                deque.pollFirst();
            }
            if (deque.size() >= properties.getRateLimit().getLoginFailPerAccount15min()) {
                loginPausedUntil.put(account,
                        now + properties.getRateLimit().getLoginPauseMinutes() * 60_000L);
            }
        }
    }

    public void recordLoginAttempt(String ip) {
        long now = Instant.now().toEpochMilli();
        Deque<Long> deque = loginIpAttempts.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(now);
            while (!deque.isEmpty() && deque.peekFirst() < now - 60_000) {
                deque.pollFirst();
            }
        }
    }

    // ---------- check-in ----------

    public void checkCheckinAllowed(Long userId, Long orderId, String ip) {
        long now = Instant.now().toEpochMilli();
        String orderKey = userId + ":" + orderId;
        if (countWithin(checkinOrderErrors, orderKey, now, 300_000)
                >= properties.getRateLimit().getCheckinPerOrder5min()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "签到错误次数过多，请稍后再试", 300);
        }
        if (countWithin(checkinIpAttempts, ip, now, 60_000)
                >= properties.getRateLimit().getCheckinPerIpPerMinute()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "操作过于频繁", 60);
        }
    }

    public void recordCheckinFailure(Long userId, Long orderId, String ip) {
        long now = Instant.now().toEpochMilli();
        add(checkinOrderErrors, userId + ":" + orderId, now, 300_000);
        add(checkinIpAttempts, ip, now, 60_000);
    }

    private void add(Map<String, Deque<Long>> map, String key, long now, long windowMs) {
        Deque<Long> deque = map.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(now);
            while (!deque.isEmpty() && deque.peekFirst() < now - windowMs) {
                deque.pollFirst();
            }
        }
    }

    private int countWithin(Map<String, Deque<Long>> map, String key, long now, long windowMs) {
        Deque<Long> deque = map.get(key);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < now - windowMs) {
                deque.pollFirst();
            }
            return deque.size();
        }
    }
}
