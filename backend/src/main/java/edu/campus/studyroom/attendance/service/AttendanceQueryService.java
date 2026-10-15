package edu.campus.studyroom.attendance.service;

import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.attendance.dto.AttendanceResult;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.settlement.domain.SettlementResult;
import edu.campus.studyroom.settlement.mapper.SettlementResultMapper;
import org.springframework.stereotype.Service;

/**
 * Attendance read side for idempotent command replays. A replayed checkout returns
 * the ORIGINAL settlement values stored in settlement_result — never a value
 * re-derived from a later credit balance.
 */
@Service
public class AttendanceQueryService {

    private final ReservationMapper reservationMapper;
    private final SettlementResultMapper settlementResultMapper;
    private final BusinessClock clock;

    public AttendanceQueryService(ReservationMapper reservationMapper,
                                  SettlementResultMapper settlementResultMapper,
                                  BusinessClock clock) {
        this.reservationMapper = reservationMapper;
        this.settlementResultMapper = settlementResultMapper;
        this.clock = clock;
    }

    /**
     * Replay view for a repeated attendance command key. Ownership is enforced
     * (non-owner gets 404). Eligible seconds and rewards come from settlement_result
     * when the order has been settled; in-flight orders report zero settlement values.
     */
    public AttendanceResult replayCheckout(Long reservationId, Long userId) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null || !reservation.getUserId().equals(userId)) {
            throw BusinessException.notFound("预约不存在或无权访问");
        }
        SettlementResult stored = settlementResultMapper.selectByReservationId(reservationId);
        return new AttendanceResult(
                String.valueOf(reservation.getId()),
                reservation.getStatus().name(),
                clock.toOffset(reservation.getCheckInAt()),
                clock.toOffset(reservation.getLeaveDueAt()),
                clock.toOffset(reservation.getFinishedAt()),
                reservation.getLeaveCount() == null ? 0 : reservation.getLeaveCount(),
                stored == null || stored.getEligibleSeconds() == null ? 0L : stored.getEligibleSeconds(),
                stored == null || stored.getRewardRequested() == null ? 0 : stored.getRewardRequested(),
                stored == null || stored.getRewardActual() == null ? 0 : stored.getRewardActual());
    }
}
