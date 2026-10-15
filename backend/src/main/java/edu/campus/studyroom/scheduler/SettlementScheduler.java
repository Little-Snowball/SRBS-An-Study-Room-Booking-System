package edu.campus.studyroom.scheduler;

import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.settlement.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 到期结算扫描（手册 8.5：任务每 60 秒）。每轮取一次时钟 now，按候选列表
 * 逐条以独立 guard 短事务结算（候选不是授权与状态依据，逐条重读重判）。
 * 单订单失败只记录脱敏 orderId/traceId 并跳过，不终止整轮，也不在轮内无限
 * 重试同一失败订单；任务不删除订单、不复原终态。
 */
@Component
public class SettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(SettlementScheduler.class);

    /** 每批最多结算的订单数。 */
    static final int BATCH_LIMIT = 100;

    private final SettlementService settlementService;
    private final BusinessClock clock;

    public SettlementScheduler(SettlementService settlementService, BusinessClock clock) {
        this.settlementService = settlementService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60000)
    public void sweepDueOrders() {
        LocalDateTime now = clock.nowLocal();
        try {
            int settled = settlementService.settleBatch(now, BATCH_LIMIT);
            if (settled > 0) {
                log.info("定时结算完成 settled={} now={}", settled, now);
            }
        } catch (Exception e) {
            // 整轮失败不抛出，避免调度线程中断；下一轮（60s 后）继续
            log.error("定时结算整轮失败 traceId={}", MDC.get("traceId"), e);
        }
    }
}
