package edu.campus.studyroom.common;

import edu.campus.studyroom.common.domain.CommandDedup;
import edu.campus.studyroom.common.mapper.CommandDedupMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Write-command idempotency. The same key with different parameters is rejected;
 * a successful same-key replay re-fetches the current view under a fresh permission
 * check instead of replaying stale data.
 */
@Service
public class IdempotencyService {

    /** Thrown when a command detects a successful same-key replay inside its transaction. */
    public static class IdempotentReplayException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;
        private final String resultType;
        private final String resultId;

        public IdempotentReplayException(String resultType, String resultId) {
            super("idempotent replay");
            this.resultType = resultType;
            this.resultId = resultId;
        }

        public String getResultType() {
            return resultType;
        }

        public String getResultId() {
            return resultId;
        }
    }

    private final CommandDedupMapper dedupMapper;

    public IdempotencyService(CommandDedupMapper dedupMapper) {
        this.dedupMapper = dedupMapper;
    }

    /**
     * Pre-checks a command key outside the guarded transaction.
     *
     * @return a replay marker when the same key+hash already succeeded
     */
    public Optional<Replay> preCheck(Long userId, String commandType, String requestKey, String requestHash) {
        CommandDedup existing = dedupMapper.selectByKey(userId, commandType, requestKey);
        if (existing == null) {
            return Optional.empty();
        }
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "幂等键已被不同参数的请求使用，请更换请求键");
        }
        return Optional.of(new Replay(existing.getResultType(), existing.getResultId()));
    }

    /**
     * In-transaction check. Under the guard, concurrent same-key commands are
     * serialized: the first commits the dedup row, the rest must not re-execute.
     * Plain read: it must never mark the surrounding command transaction rollback-only.
     */
    public void checkInTransaction(Long userId, String commandType, String requestKey, String requestHash) {
        CommandDedup existing = dedupMapper.selectByKey(userId, commandType, requestKey);
        if (existing == null) {
            return;
        }
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "幂等键已被不同参数的请求使用，请更换请求键");
        }
        throw new IdempotentReplayException(existing.getResultType(), existing.getResultId());
    }

    /** Records a successful command inside the command transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(Long userId, String commandType, String requestKey, String requestHash,
                              String resultType, String resultId) {
        CommandDedup dedup = new CommandDedup();
        dedup.setUserId(userId);
        dedup.setCommandType(commandType);
        dedup.setRequestKey(requestKey);
        dedup.setRequestHash(requestHash);
        dedup.setResultType(resultType);
        dedup.setResultId(resultId);
        dedup.setCreatedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
        dedupMapper.insert(dedup);
    }

    /** Replay marker: the controller re-fetches the current view with permission checks. */
    public record Replay(String resultType, String resultId) {
    }

    /** Retention cleanup entry point (at least 7 days). */
    @Transactional
    public int cleanupOlderThanDays(int days) {
        LocalDateTime before = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(days);
        int removed = 0;
        for (CommandDedup stale : dedupMapper.selectOlderThan(before)) {
            removed += dedupMapper.deleteById(stale.getId());
        }
        return removed;
    }

    static String traceId() {
        return MDC.get("traceId");
    }
}
