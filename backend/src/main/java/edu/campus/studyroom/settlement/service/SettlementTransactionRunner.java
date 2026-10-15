package edu.campus.studyroom.settlement.service;

import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.rule.BusinessGuardLock;
import edu.campus.studyroom.settlement.SettlementOutcome;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 结算事务边界 bean。存在的唯一原因：Spring 的 @Transactional 基于代理，
 * 同类自调用（self-invocation）不走代理，因此 settleBatch 循环里直接调
 * this.settleOne 会丢失事务。把「guard 锁 → 重读订单 → settleInternal」
 * 的短事务逻辑放到这个独立 @Component，由调度器/补扫/命令经 Spring 代理调用。
 * <p>传播行为用 REQUIRED 而非 REQUIRES_NEW：命令 Service 在自有事务内发现已到
 * 截止时复用 settleOne，此时应并入命令事务（同事务，契约 8.2），命令失败整体回滚；
 * 调度器等无事务调用方调用时则开启独立短事务。 Isolation READ_COMMITTED 与
 * 核心写命令一致。
 */
@Component
public class SettlementTransactionRunner {

    private final SettlementServiceImpl settlementService;
    private final BusinessGuardLock businessGuardLock;
    private final ReservationMapper reservationMapper;

    public SettlementTransactionRunner(SettlementServiceImpl settlementService,
                                       BusinessGuardLock businessGuardLock,
                                       ReservationMapper reservationMapper) {
        this.settlementService = settlementService;
        this.businessGuardLock = businessGuardLock;
        this.reservationMapper = reservationMapper;
    }

    /**
     * 单订单结算短事务：锁 guard → FOR UPDATE 重读 → 终态结算。
     * 候选列表不是授权与状态依据，一切以这里重读到的订单为准。
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public SettlementOutcome settleOneTx(Long reservationId, LocalDateTime now, String trigger) {
        businessGuardLock.lock();
        Reservation reservation = reservationMapper.selectByIdForUpdate(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在: " + reservationId);
        }
        return settlementService.settleInternal(reservation, now, trigger);
    }
}
