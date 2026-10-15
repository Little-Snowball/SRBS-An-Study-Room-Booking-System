package edu.campus.studyroom.reservation.service;

import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.dto.ReservationDetail;
import edu.campus.studyroom.reservation.dto.ReservationListItem;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.rule.RuleService;
import edu.campus.studyroom.rule.domain.RuleConfig;
import edu.campus.studyroom.user.domain.AppUser;
import edu.campus.studyroom.user.mapper.AppUserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reservation read side. Ownership is enforced here: a non-owner non-admin gets
 * NOT_FOUND (404) so existence never leaks. allowedActions is a UI hint computed
 * from the order's own rule snapshot; the server still re-decides every command.
 */
@Service
public class ReservationQueryService {

    private final ReservationMapper reservationMapper;
    private final RuleService ruleService;
    private final AppUserMapper appUserMapper;
    private final BusinessClock clock;

    public ReservationQueryService(ReservationMapper reservationMapper, RuleService ruleService,
                                   AppUserMapper appUserMapper, BusinessClock clock) {
        this.reservationMapper = reservationMapper;
        this.ruleService = ruleService;
        this.appUserMapper = appUserMapper;
        this.clock = clock;
    }

    /** Detail for the owning student or any admin; others get 404. */
    public ReservationDetail getDetailForUser(Long reservationId, Long userId, boolean isAdmin) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw BusinessException.notFound("预约不存在");
        }
        if (!isAdmin && !reservation.getUserId().equals(userId)) {
            throw BusinessException.notFound("预约不存在或无权访问");
        }
        return toDetail(reservation, ruleFor(reservation));
    }

    /** Page of my reservations with optional status and start-date range filters. */
    public PageResult<ReservationListItem> listMine(Long userId, ReservationStatus status,
                                                    LocalDate dateFrom, LocalDate dateTo, PageQuery pageQuery) {
        List<Reservation> rows = reservationMapper.selectByUserIdPage(userId, status, dateFrom, dateTo,
                pageQuery.offset(), pageQuery.size());
        long total = reservationMapper.countByUserId(userId, status, dateFrom, dateTo);
        List<ReservationListItem> items = new ArrayList<>(rows.size());
        for (Reservation row : rows) {
            items.add(toListItem(row, ruleFor(row)));
        }
        return PageResult.of(items, total, pageQuery.page(), pageQuery.size());
    }

    /**
     * Admin reservation query: cross-student page with account prefix / room /
     * status / start-date filters. Each row carries the owning student's account
     * and name. Read-only: no proxy check-in is ever offered by these views.
     */
    public PageResult<ReservationListItem> listAdmin(String account, Long roomId, ReservationStatus status,
                                                     LocalDate dateFrom, LocalDate dateTo,
                                                     PageQuery pageQuery) {
        List<Reservation> rows = reservationMapper.selectAdminPage(account, roomId, status, dateFrom, dateTo,
                pageQuery.offset(), pageQuery.size());
        long total = reservationMapper.countAdminPage(account, roomId, status, dateFrom, dateTo);
        List<ReservationListItem> items = new ArrayList<>(rows.size());
        for (Reservation row : rows) {
            AppUser owner = appUserMapper.selectById(row.getUserId());
            items.add(new ReservationListItem(
                    String.valueOf(row.getId()),
                    row.getOrderNo(),
                    row.getRoomNameSnapshot(),
                    row.getSeatCodeSnapshot(),
                    clock.toOffset(row.getStartAt()),
                    clock.toOffset(row.getEndAt()),
                    row.getStatus().name(),
                    row.getTerminationReason() == null ? null : row.getTerminationReason().name(),
                    computeAllowedActions(row, clock.nowLocal(), ruleFor(row)),
                    owner == null ? null : owner.getAccount(),
                    owner == null ? null : owner.getName()));
        }
        return PageResult.of(items, total, pageQuery.page(), pageQuery.size());
    }

    /**
     * Rule that governs an order: the snapshot taken at creation, falling back to
     * the active rule when the snapshot is missing or unreadable.
     */
    public RuleConfig ruleFor(Reservation reservation) {
        String snapshot = reservation.getRuleSnapshot();
        if (snapshot != null && !snapshot.isBlank()) {
            try {
                return RuleConfig.fromJson(snapshot);
            } catch (RuntimeException ex) {
                // fall through to the active rule
            }
        }
        return ruleService.activeRule();
    }

    public ReservationDetail toDetail(Reservation reservation, RuleConfig rule) {
        AppUser owner = appUserMapper.selectById(reservation.getUserId());
        return new ReservationDetail(
                String.valueOf(reservation.getId()),
                reservation.getOrderNo(),
                String.valueOf(reservation.getUserId()),
                owner == null ? null : owner.getAccount(),
                owner == null ? null : owner.getName(),
                String.valueOf(reservation.getRoomId()),
                reservation.getRoomNameSnapshot(),
                String.valueOf(reservation.getSeatId()),
                reservation.getSeatCodeSnapshot(),
                clock.toOffset(reservation.getStartAt()),
                clock.toOffset(reservation.getEndAt()),
                clock.toOffset(reservation.getCheckInAt()),
                clock.toOffset(reservation.getCheckinDueAt()),
                clock.toOffset(reservation.getCancelDueAt()),
                clock.toOffset(reservation.getLeaveDueAt()),
                clock.toOffset(reservation.getFinishedAt()),
                reservation.getStatus().name(),
                reservation.getTerminationReason() == null ? null : reservation.getTerminationReason().name(),
                reservation.getLeaveCount() == null ? 0 : reservation.getLeaveCount(),
                String.valueOf(reservation.getVersion()),
                computeAllowedActions(reservation, clock.nowLocal(), rule),
                ruleSummary(rule));
    }

    public ReservationListItem toListItem(Reservation reservation, RuleConfig rule) {
        return new ReservationListItem(
                String.valueOf(reservation.getId()),
                reservation.getOrderNo(),
                reservation.getRoomNameSnapshot(),
                reservation.getSeatCodeSnapshot(),
                clock.toOffset(reservation.getStartAt()),
                clock.toOffset(reservation.getEndAt()),
                reservation.getStatus().name(),
                reservation.getTerminationReason() == null ? null : reservation.getTerminationReason().name(),
                computeAllowedActions(reservation, clock.nowLocal(), rule),
                null,
                null);
    }

    /**
     * Button hints. Half-open [start, end) semantics; every deadline comparison is
     * millisecond strict. Terminal orders expose no actions. The hint never
     * authorizes anything — each command re-validates server-side.
     */
    public List<String> computeAllowedActions(Reservation reservation, LocalDateTime now, RuleConfig rule) {
        List<String> actions = new ArrayList<>(4);
        ReservationStatus status = reservation.getStatus();
        if (status == ReservationStatus.RESERVED) {
            if (!now.isAfter(reservation.getCancelDueAt())) {
                actions.add("CANCEL");
            }
            LocalDateTime windowOpen = reservation.getStartAt().minusMinutes(rule.checkinEarlyMinutes());
            LocalDateTime windowClose = reservation.getStartAt().plusMinutes(rule.checkinLateMinutes());
            if (!now.isBefore(windowOpen) && !now.isAfter(windowClose) && now.isBefore(reservation.getEndAt())) {
                actions.add("CHECK_IN");
            }
        } else if (status == ReservationStatus.IN_USE) {
            if (reservation.getLeaveCount() != null && reservation.getLeaveCount() < rule.leaveMaxCount()) {
                actions.add("LEAVE");
            }
            actions.add("CHECK_OUT");
        } else if (status == ReservationStatus.LEFT_TEMP) {
            actions.add("RETURN");
            actions.add("CHECK_OUT");
        }
        return actions;
    }

    /** One-line human summary of the governing rule snapshot. */
    public String ruleSummary(RuleConfig rule) {
        return String.format("开始前%d分钟可免费取消；签到窗口为开始前%d分钟至开始后%d分钟；暂离最多%d次、每次%d分钟；信用分需达到%d分",
                rule.cancelBeforeMinutes(), rule.checkinEarlyMinutes(), rule.checkinLateMinutes(),
                rule.leaveMaxCount(), rule.leaveMaxMinutes(), rule.minCredit());
    }
}
