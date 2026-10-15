package edu.campus.studyroom.scheduler;

import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.settlement.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动补扫（手册 8.5：服务停止后重启立即补扫，直到候选清空）。
 * 补扫是安全操作（只把到截止的订单结算到终态，不删单不复原），所有 profile
 * 均允许执行。设轮次上限防止异常数据导致死循环；达到上限后由 60 秒定时任务继续。
 */
@Component
public class StartupSweepRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSweepRunner.class);

    /** 补扫轮次上限：每轮最多 BATCH_LIMIT 单，超过上限交给定时任务。 */
    static final int MAX_ROUNDS = 50;

    private final SettlementService settlementService;
    private final BusinessClock clock;

    public StartupSweepRunner(SettlementService settlementService, BusinessClock clock) {
        this.settlementService = settlementService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            int settled;
            try {
                settled = settlementService.settleBatch(clock.nowLocal(), SettlementScheduler.BATCH_LIMIT);
            } catch (Exception e) {
                // 补扫失败不阻断应用启动，由 60 秒定时任务继续
                log.error("启动补扫第 {} 轮失败", round, e);
                return;
            }
            if (settled == 0) {
                log.info("启动补扫完成，无更多待结算候选（第 {} 轮）", round);
                return;
            }
            log.info("启动补扫第 {} 轮结算 {} 单", round, settled);
        }
        log.warn("启动补扫达到轮次上限 {}，剩余候选交由定时结算继续处理", MAX_ROUNDS);
    }
}
