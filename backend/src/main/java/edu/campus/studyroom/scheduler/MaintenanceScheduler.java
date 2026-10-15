package edu.campus.studyroom.scheduler;

import edu.campus.studyroom.auth.mapper.AuthSessionMapper;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定期维护（保留期至少 7 天，手册 7.2.9/7.2.10）：
 * <ul>
 *   <li>过期会话：auth_session.expires_at 早于 now-7 天；</li>
 *   <li>幂等记录：command_dedup 超过 7 天；</li>
 *   <li>孤立 TEMP 文件：file 模块当前未提供 FileMaintenanceService 之类的清理钩子
 *       （仅有 FileObjectMapper），TEMP 文件清理需结合存储目录删除与归属/状态校验，
 *       由 file 模块的服务层负责，本任务不越过其边界直接删库行。</li>
 * </ul>
 */
@Component
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private static final int RETENTION_DAYS = 7;

    private final AuthSessionMapper authSessionMapper;
    private final IdempotencyService idempotencyService;
    private final BusinessClock clock;

    public MaintenanceScheduler(AuthSessionMapper authSessionMapper, IdempotencyService idempotencyService,
                                BusinessClock clock) {
        this.authSessionMapper = authSessionMapper;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 3600000)
    public void cleanupStaleData() {
        LocalDateTime now = clock.nowLocal();
        // 两项清理相互独立：一项失败不影响另一项
        try {
            int sessions = authSessionMapper.deleteExpiredBefore(now.minusDays(RETENTION_DAYS));
            log.info("清理过期会话 removed={} before={}", sessions, now.minusDays(RETENTION_DAYS));
        } catch (Exception e) {
            log.error("清理过期会话失败", e);
        }
        try {
            int dedup = idempotencyService.cleanupOlderThanDays(RETENTION_DAYS);
            log.info("清理幂等记录 removed={}", dedup);
        } catch (Exception e) {
            log.error("清理幂等记录失败", e);
        }
    }
}
