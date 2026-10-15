package edu.campus.studyroom.settlement.service;

import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.rule.BusinessGuardLock;
import edu.campus.studyroom.settlement.PreflightSettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 前置结算（契约 8.2）。在命令事务之前以独立短事务提交应结算的订单：
 * 即使随后命令业务失败，这些结算已提交、不回滚。调用方：预约创建、签到、
 * 暂离/回座、签退等命令的 Controller 前置。
 * <p>两阶段之间可能被其他命令插入新状态，命令阶段必须重读重验（由命令服务保证）。
 */
@Service
public class PreflightSettlementServiceImpl implements PreflightSettlementService {

    private final SettlementServiceImpl settlementService;
    private final BusinessGuardLock businessGuardLock;
    private final ReservationMapper reservationMapper;

    public PreflightSettlementServiceImpl(SettlementServiceImpl settlementService,
                                          BusinessGuardLock businessGuardLock,
                                          ReservationMapper reservationMapper) {
        this.settlementService = settlementService;
        this.businessGuardLock = businessGuardLock;
        this.reservationMapper = reservationMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public void preflightForReservationCreate(Long seatId, Long userId, LocalDateTime now) {
        // guard 串行化全部核心写，锁内plain read 即一致快照
        businessGuardLock.lock();
        Set<Long> seen = new LinkedHashSet<>();
        // 目标座位的有效订单（同人同座会同时出现在两个列表，按 id 去重）
        for (Reservation reservation : reservationMapper.selectEffectiveBySeatId(seatId)) {
            if (seen.add(reservation.getId())) {
                settlementService.settleInternal(reservation, now, "PREFLIGHT");
            }
        }
        // 该用户的有效订单（避免同用户同时段其他座位的订单绕过截止）
        for (Reservation reservation : reservationMapper.selectEffectiveByUserId(userId)) {
            if (seen.add(reservation.getId())) {
                settlementService.settleInternal(reservation, now, "PREFLIGHT");
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public void preflightForOrder(Long reservationId, LocalDateTime now) {
        businessGuardLock.lock();
        Reservation reservation = reservationMapper.selectByIdForUpdate(reservationId);
        if (reservation == null || !reservation.getStatus().isEffective()) {
            // 不存在或已终态：无需前置结算，命令层自行重读重验
            return;
        }
        settlementService.settleInternal(reservation, now, "PREFLIGHT");
    }
}
