package edu.campus.studyroom.resource.service;

import edu.campus.studyroom.notification.NotificationService;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.domain.TerminationReason;
import edu.campus.studyroom.resource.domain.OrderPolicy;
import edu.campus.studyroom.resource.domain.RetainedService;
import edu.campus.studyroom.resource.mapper.RetainedServiceMapper;
import edu.campus.studyroom.settlement.SettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Shared order cascade for resource changes (room close / room hours change /
 * seat maintenance). Must run inside the caller's guarded transaction: it
 * terminates orders through {@link SettlementService#adminTerminate} (which
 * joins the transaction and only re-reads rows), writes retained-service
 * permission intervals, and publishes same-transaction notifications.
 *
 * <p>RESERVED orders are always cancelled without fault; the order policy only
 * decides the fate of IN_USE/LEFT_TEMP orders (force-end vs keep active).
 */
@Service
public class OrderCascadeService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RetainedServiceMapper retainedServiceMapper;
    private final SettlementService settlementService;
    private final NotificationService notificationService;

    public OrderCascadeService(RetainedServiceMapper retainedServiceMapper, SettlementService settlementService,
                               NotificationService notificationService) {
        this.retainedServiceMapper = retainedServiceMapper;
        this.settlementService = settlementService;
        this.notificationService = notificationService;
    }

    /**
     * @param orders          effective orders to process (already loaded under the guard)
     * @param reason          no-fault termination reason recorded on each order
     * @param policy          how IN_USE/LEFT_TEMP orders are handled
     * @param causeEventId    the resource_status_event id (retained_service foreign key)
     * @param eventKeyPrefix  notification key prefix; the full key is
*                        {@code prefix + ":order:" + reservationId} so one change
     *                        never reuses another change's notification key
     * @param changeDesc      human-readable cause used in notification bodies
     */
    @Transactional(propagation = Propagation.MANDATORY, isolation = Isolation.READ_COMMITTED)
    public PolicyCounters apply(List<Reservation> orders, TerminationReason reason, OrderPolicy policy,
                                Long causeEventId, String eventKeyPrefix, String changeDesc,
                                LocalDateTime now) {
        int cancelled = 0;
        int kept = 0;
        int ended = 0;
        for (Reservation order : orders) {
            if (order.getStatus() == ReservationStatus.RESERVED) {
                settlementService.adminTerminate(order.getId(), now, reason);
                cancelled++;
                notificationService.publish(order.getUserId(), eventKeyPrefix + ":order:" + order.getId(),
                        "RESOURCE_CANCEL", "预约已取消",
                        "由于" + changeDesc + "，您的预约（" + order.getOrderNo() + "，"
                                + order.getStartAt().format(TIME_FMT) + "—"
                                + order.getEndAt().format(TIME_FMT) + "）已无责取消，不影响信用分。",
                        order.getId(), null, now);
            } else if (policy == OrderPolicy.FORCE_END) {
                settlementService.adminTerminate(order.getId(), now, reason);
                ended++;
                notificationService.publish(order.getUserId(), eventKeyPrefix + ":order:" + order.getId(),
                        "RESOURCE_END", "预约已提前结束",
                        "由于" + changeDesc + "，您的进行中预约（" + order.getOrderNo()
                                + "）已提前结束，零奖励零扣分。",
                        order.getId(), null, now);
            } else {
                // KEEP_ACTIVE：保留进行中状态，写入保留使用权许可区间 [now, end_at)
                if (now.isBefore(order.getEndAt())) {
                    RetainedService retained = new RetainedService();
                    retained.setReservationId(order.getId());
                    retained.setSeatId(order.getSeatId());
                    retained.setFromAt(now);
                    retained.setToAt(order.getEndAt());
                    retained.setCauseEventId(causeEventId);
                    retained.setCreatedAt(now);
                    retainedServiceMapper.insert(retained);
                }
                kept++;
                notificationService.publish(order.getUserId(), eventKeyPrefix + ":order:" + order.getId(),
                        "RESOURCE_KEEP", "预约可继续使用",
                        "由于" + changeDesc + "，您的进行中预约（" + order.getOrderNo()
                                + "）可继续使用至原结束时间 " + order.getEndAt().format(TIME_FMT) + "。",
                        order.getId(), null, now);
            }
        }
        return new PolicyCounters(cancelled, kept, ended);
    }

    /** Counters of one cascade run. */
    public record PolicyCounters(int cancelled, int kept, int ended) {
    }
}
