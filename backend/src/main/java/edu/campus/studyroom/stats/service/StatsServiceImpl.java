package edu.campus.studyroom.stats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.reservation.domain.AttendanceEvent;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.mapper.AttendanceEventMapper;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.resource.domain.ResourceStatusEvent;
import edu.campus.studyroom.resource.domain.ResourceType;
import edu.campus.studyroom.resource.domain.RetainedService;
import edu.campus.studyroom.resource.domain.RoomStatus;
import edu.campus.studyroom.resource.domain.Seat;
import edu.campus.studyroom.resource.domain.SeatStatus;
import edu.campus.studyroom.resource.domain.StudyRoom;
import edu.campus.studyroom.resource.mapper.ResourceStatusEventMapper;
import edu.campus.studyroom.resource.mapper.RetainedServiceMapper;
import edu.campus.studyroom.resource.mapper.SeatMapper;
import edu.campus.studyroom.resource.mapper.StudyRoomMapper;
import edu.campus.studyroom.settlement.domain.EligibleTimeCalculator;
import edu.campus.studyroom.settlement.domain.Violation;
import edu.campus.studyroom.settlement.domain.ViolationStatus;
import edu.campus.studyroom.settlement.mapper.ViolationMapper;
import edu.campus.studyroom.stats.dto.OccupancyView;
import edu.campus.studyroom.stats.dto.OverviewResult;
import edu.campus.studyroom.stats.dto.RoomUsageRow;
import edu.campus.studyroom.stats.dto.TrafficBucket;
import edu.campus.studyroom.stats.dto.TrafficResult;
import edu.campus.studyroom.stats.dto.WeekViolationView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only statistics with hand-verifiable raw values. No guard lock is taken:
 * every query is a single consistent read or a single-pass aggregation over one
 * snapshot of the rows.
 */
@Service
public class StatsServiceImpl implements StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsServiceImpl.class);

    private static final int BUCKET_MINUTES = 30;
    private static final int BUCKETS_PER_DAY = 48;
    private static final int USAGE_MAX_DAYS = 31;

    private final ReservationMapper reservationMapper;
    private final AttendanceEventMapper attendanceEventMapper;
    private final StudyRoomMapper studyRoomMapper;
    private final SeatMapper seatMapper;
    private final ResourceStatusEventMapper resourceStatusEventMapper;
    private final RetainedServiceMapper retainedServiceMapper;
    private final ViolationMapper violationMapper;
    private final BusinessClock clock;
    private final ObjectMapper objectMapper;

    public StatsServiceImpl(ReservationMapper reservationMapper, AttendanceEventMapper attendanceEventMapper,
                            StudyRoomMapper studyRoomMapper, SeatMapper seatMapper,
                            ResourceStatusEventMapper resourceStatusEventMapper,
                            RetainedServiceMapper retainedServiceMapper, ViolationMapper violationMapper,
                            BusinessClock clock, ObjectMapper objectMapper) {
        this.reservationMapper = reservationMapper;
        this.attendanceEventMapper = attendanceEventMapper;
        this.studyRoomMapper = studyRoomMapper;
        this.seatMapper = seatMapper;
        this.resourceStatusEventMapper = resourceStatusEventMapper;
        this.retainedServiceMapper = retainedServiceMapper;
        this.violationMapper = violationMapper;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public OverviewResult overview(LocalDate date) {
        LocalDate target = date == null ? clock.today() : date;
        LocalDateTime now = clock.nowLocal();

        long todayReservationCount = reservationMapper.countByStartDate(target);
        long cancelledCount = reservationMapper.countCancelledByStartDate(target);
        long distinctStudentCount = reservationMapper.countDistinctStudentByStartDate(target);

        // Occupancy snapshot over OPEN rooms' NORMAL seats.
        LocalDateTime dayFrom = target.minusDays(1).atStartOfDay();
        LocalDateTime dayTo = target.plusDays(2).atStartOfDay();
        List<Reservation> nearbyOrders = reservationMapper.selectByStartBetween(dayFrom, dayTo);
        List<StudyRoom> rooms = studyRoomMapper.selectAll();
        Set<Long> servableSeatIds = new HashSet<>();
        List<Long> allSeatIds = new ArrayList<>();
        for (StudyRoom room : rooms) {
            for (Seat seat : seatMapper.selectByRoomId(room.getId())) {
                allSeatIds.add(seat.getId());
                if (room.getStatus() == RoomStatus.OPEN && seat.getStatus() == SeatStatus.NORMAL) {
                    servableSeatIds.add(seat.getId());
                }
            }
        }
        Set<Long> occupiedSeats = new HashSet<>();
        for (Reservation order : nearbyOrders) {
            if (order.getStatus() != ReservationStatus.IN_USE || !servableSeatIds.contains(order.getSeatId())) {
                continue;
            }
            // effective_start = min(start_at, check_in_at); LEFT_TEMP never counts.
            LocalDateTime effectiveStart = order.getCheckInAt() == null
                    ? order.getStartAt()
                    : (order.getCheckInAt().isBefore(order.getStartAt()) ? order.getCheckInAt() : order.getStartAt());
            if (!effectiveStart.isAfter(now) && now.isBefore(order.getEndAt())) {
                occupiedSeats.add(order.getSeatId());
            }
        }
        long keptActiveSeats = 0;
        if (!allSeatIds.isEmpty()) {
            Set<Long> retainedSeats = new HashSet<>();
            for (RetainedService retained : retainedServiceMapper.selectBySeatIdsBetween(
                    allSeatIds, now, now.plusSeconds(1))) {
                retainedSeats.add(retained.getSeatId());
            }
            keptActiveSeats = retainedSeats.size();
        }
        OccupancyView occupancy = new OccupancyView(occupiedSeats.size(), servableSeatIds.size(),
                rate(occupiedSeats.size(), servableSeatIds.size()), keptActiveSeats, clock.toOffset(now));

        WeekViolationView weekViolation = weekViolation(target, now);
        return new OverviewResult(clock.toOffset(target.atStartOfDay()), todayReservationCount, cancelledCount,
                distinctStudentCount, occupancy, weekViolation);
    }

    @Override
    public TrafficResult traffic(LocalDate date, Long roomId) {
        LocalDate target = date == null ? clock.today() : date;
        LocalDateTime from = target.atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        List<AttendanceEvent> checkIns = attendanceEventMapper.selectCheckInsBetween(from, to, roomId);
        long[] counts = new long[BUCKETS_PER_DAY];
        for (AttendanceEvent event : checkIns) {
            int minuteOfDay = event.getEventAt().getHour() * 60 + event.getEventAt().getMinute();
            int bucket = minuteOfDay / BUCKET_MINUTES;
            if (bucket >= 0 && bucket < BUCKETS_PER_DAY) {
                counts[bucket]++;
            }
        }
        List<TrafficBucket> buckets = new ArrayList<>(BUCKETS_PER_DAY);
        for (int i = 0; i < BUCKETS_PER_DAY; i++) {
            buckets.add(new TrafficBucket(clock.toOffset(from.plusMinutes((long) i * BUCKET_MINUTES)),
                    counts[i]));
        }
        return new TrafficResult(clock.toOffset(from), roomId, BUCKETS_PER_DAY, buckets);
    }

    @Override
    public PageResult<RoomUsageRow> roomUsage(LocalDate dateFrom, LocalDate dateTo, PageQuery pageQuery) {
        if (dateFrom == null || dateTo == null) {
            throw BusinessException.validation("dateFrom 与 dateTo 不能为空");
        }
        long days = ChronoUnit.DAYS.between(dateFrom, dateTo);
        if (days < 0) {
            throw BusinessException.validation("dateFrom 不能晚于 dateTo");
        }
        if (days > USAGE_MAX_DAYS) {
            throw BusinessException.validation("统计区间上限为 31 天");
        }
        LocalDateTime from = dateFrom.atStartOfDay();
        LocalDateTime to = dateTo.plusDays(1).atStartOfDay();
        LocalDateTime asOf = clock.nowLocal();

        List<StudyRoom> rooms = studyRoomMapper.selectAll();
        List<Reservation> reservations = reservationMapper.selectByStartBetween(from, to);
        Map<Long, List<Reservation>> ordersBySeat = new LinkedHashMap<>();
        for (Reservation order : reservations) {
            ordersBySeat.computeIfAbsent(order.getSeatId(), key -> new ArrayList<>()).add(order);
        }
        Map<Long, List<Seat>> seatsByRoom = new LinkedHashMap<>();
        List<Long> allSeatIds = new ArrayList<>();
        for (StudyRoom room : rooms) {
            List<Seat> seats = seatMapper.selectByRoomId(room.getId());
            seatsByRoom.put(room.getId(), seats);
            for (Seat seat : seats) {
                allSeatIds.add(seat.getId());
            }
        }
        Map<Long, List<RetainedService>> retainedBySeat = new LinkedHashMap<>();
        if (!allSeatIds.isEmpty()) {
            for (RetainedService retained : retainedServiceMapper.selectBySeatIdsBetween(allSeatIds, from, to)) {
                retainedBySeat.computeIfAbsent(retained.getSeatId(), key -> new ArrayList<>()).add(retained);
            }
        }

        List<RoomUsageRow> rows = new ArrayList<>(rooms.size());
        for (StudyRoom room : rooms) {
            long usageSeconds = 0;
            long serviceSeconds = 0;
            for (Seat seat : seatsByRoom.getOrDefault(room.getId(), List.of())) {
                List<Segment> serviceSegments = buildServiceSegments(room, seat, from, to,
                        retainedBySeat.getOrDefault(seat.getId(), List.of()));
                serviceSeconds += secondsOf(serviceSegments);
                usageSeconds += usageOfSeat(ordersBySeat.getOrDefault(seat.getId(), List.of()),
                        serviceSegments, asOf);
            }
            rows.add(new RoomUsageRow(room.getId(), room.getName(), usageSeconds, serviceSeconds,
                    rate(usageSeconds, serviceSeconds)));
        }
        rows.sort(Comparator.comparing(RoomUsageRow::usageRate,
                        Comparator.<Double>nullsLast(Comparator.reverseOrder()))
                .thenComparing(RoomUsageRow::usageSeconds, Comparator.<Long>reverseOrder())
                .thenComparing(RoomUsageRow::roomId));

        int total = rows.size();
        int offset = pageQuery.offset();
        int end = Math.min(total, offset + pageQuery.size());
        List<RoomUsageRow> items = offset >= total ? List.of() : new ArrayList<>(rows.subList(offset, end));
        return PageResult.of(items, total, pageQuery.page(), pageQuery.size());
    }

    // ---- week violation rate -------------------------------------------------

    private WeekViolationView weekViolation(LocalDate date, LocalDateTime asOfAt) {
        LocalDate monday = date.with(DayOfWeek.MONDAY);
        LocalDateTime weekStart = monday.atStartOfDay();
        LocalDateTime weekEnd = monday.plusWeeks(1).atStartOfDay();
        List<Reservation> weekOrders = reservationMapper.selectByStartBetween(weekStart, weekEnd);

        long denominator = 0;
        long numerator = 0;
        long settlementPending = 0;
        for (Reservation order : weekOrders) {
            if (order.getStatus() == ReservationStatus.CANCELLED) {
                continue;
            }
            boolean settled = order.getStatus().isTerminal() || !order.getEndAt().isAfter(asOfAt);
            if (settled) {
                denominator++;
                if (order.getStatus() == ReservationStatus.VIOLATED) {
                    Violation violation = violationMapper.selectByReservationId(order.getId());
                    if (violation != null && violation.getStatus() == ViolationStatus.ACTIVE) {
                        numerator++;
                    }
                }
                continue;
            }
            if (terminalDue(order, asOfAt)) {
                settlementPending++;
            }
        }
        return new WeekViolationView(clock.toOffset(weekStart), clock.toOffset(weekEnd), clock.toOffset(asOfAt),
                numerator, denominator, rate(numerator, denominator), settlementPending);
    }

    /**
     * Pure deadline derivation shared with SettlementService: the order should
     * already have reached a terminal state by now. Nothing is written and no
     * credit is deducted here.
     */
    private boolean terminalDue(Reservation order, LocalDateTime now) {
        return switch (order.getStatus()) {
            case RESERVED -> !now.isBefore(order.getCheckinDueAt()) || !now.isBefore(order.getEndAt());
            case LEFT_TEMP -> (order.getLeaveDueAt() != null && !now.isBefore(order.getLeaveDueAt()))
                    || !now.isBefore(order.getEndAt());
            case IN_USE -> !now.isBefore(order.getEndAt());
            default -> false;
        };
    }

    // ---- servable time reconstruction ----------------------------------------

    /**
     * Real in-seat seconds of one seat's IN_USE orders intersected with the
     * servable segments. The EligibleTimeCalculator pure function shared with
     * SettlementService derives in-seat segments from attendance events: early
     * check-ins before the order start are excluded, anything past the order end
     * is excluded, and LEAVE gaps (temporary absences) never count. Overlapping
     * seconds are counted once because servable segments are normalized.
     */
    private long usageOfSeat(List<Reservation> orders, List<Segment> serviceSegments, LocalDateTime asOf) {
        long usageSeconds = 0;
        for (Reservation order : orders) {
            if (order.getStatus() != ReservationStatus.IN_USE) {
                continue; // LEFT_TEMP and unstarted orders never count as usage
            }
            List<AttendanceEvent> events = attendanceEventMapper.selectByReservationId(order.getId());
            if (events.isEmpty()) {
                continue;
            }
            try {
                for (Segment segment : serviceSegments) {
                    LocalDateTime start = latestOf(order.getStartAt(), segment.start());
                    LocalDateTime end = earliestOf(order.getEndAt(), segment.end());
                    if (!start.isBefore(end)) {
                        continue;
                    }
                    // Graceful close of the open in-seat segment at the read instant.
                    usageSeconds += EligibleTimeCalculator.eligibleSeconds(events, start, end, asOf);
                }
            } catch (BusinessException ex) {
                // Inconsistent event history: skip this order, never guess seconds.
                log.warn("skip order with inconsistent attendance events orderId={}", order.getId(), ex);
            }
        }
        return usageSeconds;
    }

    /**
     * Servable seat time of one seat over [from, to): room open hours and seat
     * NORMAL status are rebuilt from the full resource_status_event history and
     * unioned with the explicit retained-service intervals.
     */
    private List<Segment> buildServiceSegments(StudyRoom room, Seat seat, LocalDateTime from, LocalDateTime to,
                                               List<RetainedService> retained) {
        List<Segment> roomOpen = roomOpenSegments(room, from, to);
        List<Segment> seatNormal = seatNormalSegments(seat, from, to);
        List<Segment> service = intersect(normalize(roomOpen), normalize(seatNormal));
        for (RetainedService interval : retained) {
            LocalDateTime start = interval.getFromAt().isBefore(from) ? from : interval.getFromAt();
            LocalDateTime end = interval.getToAt().isAfter(to) ? to : interval.getToAt();
            if (start.isBefore(end)) {
                service = union(service, List.of(new Segment(start, end)));
            }
        }
        return service;
    }

    private List<Segment> roomOpenSegments(StudyRoom room, LocalDateTime from, LocalDateTime to) {
        List<ResourceStatusEvent> events = resourceStatusEventMapper
                .selectRoomEventsBetween(room.getId(), from, to);
        RoomState base = roomStateAt(room, from, events);
        List<Segment> segments = new ArrayList<>();
        LocalDateTime cursor = from;
        RoomState current = base;
        for (ResourceStatusEvent event : events) {
            LocalDateTime at = event.getEffectiveAt();
            if (at.isBefore(from) || !at.isBefore(to) || at.isBefore(cursor)) {
                continue;
            }
            if (current != null && current.status == RoomStatus.OPEN) {
                segments.addAll(dailyOpenSegments(current, cursor, at, from, to));
            }
            cursor = at;
            current = applyRoomEvent(current, event);
        }
        if (cursor.isBefore(to) && current != null && current.status == RoomStatus.OPEN) {
            segments.addAll(dailyOpenSegments(current, cursor, to, from, to));
        }
        return segments;
    }

    private List<Segment> dailyOpenSegments(RoomState state, LocalDateTime windowStart,
                                            LocalDateTime windowEnd, LocalDateTime from, LocalDateTime to) {
        List<Segment> segments = new ArrayList<>();
        if (state.openTime == null || state.closeTime == null) {
            // Open hours unknown for this window: no servable time can be derived.
            return segments;
        }
        LocalDate firstDay = windowStart.toLocalDate();
        LocalDate lastDay = windowEnd.toLocalDate();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            LocalDateTime openAt = LocalDateTime.of(day, state.openTime);
            LocalDateTime closeAt = LocalDateTime.of(day, state.closeTime);
            LocalDateTime start = latestOf(openAt, windowStart, from);
            LocalDateTime end = earliestOf(closeAt, windowEnd, to);
            if (start.isBefore(end)) {
                segments.add(new Segment(start, end));
            }
        }
        return segments;
    }

    private RoomState roomStateAt(StudyRoom room, LocalDateTime at, List<ResourceStatusEvent> events) {
        RoomState state = null;
        for (ResourceStatusEvent event : events) {
            if (!event.getEffectiveAt().isAfter(at)) {
                state = applyRoomEvent(state, event);
            }
        }
        if (state == null) {
            ResourceStatusEvent latest = resourceStatusEventMapper.selectLatestForRoomAt(room.getId(), at);
            if (latest != null) {
                state = parseRoomState(latest.getAfterSnapshot());
            }
        }
        if (state == null) {
            // No history at all: fall back to the current row (room predates the range).
            state = new RoomState(room.getStatus(), room.getOpenTime(), room.getCloseTime());
        }
        return state;
    }

    private RoomState applyRoomEvent(RoomState current, ResourceStatusEvent event) {
        RoomState parsed = parseRoomState(event.getAfterSnapshot());
        if (parsed == null) {
            return current;
        }
        if (current == null) {
            return parsed;
        }
        return new RoomState(
                parsed.status == null ? current.status : parsed.status,
                parsed.openTime == null ? current.openTime : parsed.openTime,
                parsed.closeTime == null ? current.closeTime : parsed.closeTime);
    }

    private RoomState parseRoomState(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(snapshot);
            RoomStatus status = node.hasNonNull("status")
                    ? RoomStatus.valueOf(node.get("status").asText()) : null;
            LocalTime open = node.hasNonNull("openTime") ? LocalTime.parse(node.get("openTime").asText()) : null;
            LocalTime close = node.hasNonNull("closeTime") ? LocalTime.parse(node.get("closeTime").asText()) : null;
            return new RoomState(status, open, close);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Segment> seatNormalSegments(Seat seat, LocalDateTime from, LocalDateTime to) {
        List<ResourceStatusEvent> events = resourceStatusEventMapper.selectBySeatId(seat.getId());
        SeatState base = null;
        for (ResourceStatusEvent event : events) {
            if (!event.getEffectiveAt().isAfter(from)) {
                base = applySeatEvent(base, event);
            }
        }
        if (base == null) {
            ResourceStatusEvent latest = resourceStatusEventMapper.selectLatestForSeatAt(seat.getId(), from);
            if (latest != null) {
                base = parseSeatState(latest.getAfterSnapshot());
            }
        }
        if (base == null) {
            // No seat history: fall back to the current row (seat predates the range).
            base = new SeatState(seat.getStatus());
        }
        List<Segment> segments = new ArrayList<>();
        LocalDateTime cursor = from;
        SeatState current = base;
        for (ResourceStatusEvent event : events) {
            LocalDateTime at = event.getEffectiveAt();
            if (at.isBefore(from) || !at.isBefore(to) || at.isBefore(cursor)) {
                continue;
            }
            if (current != null && current.status == SeatStatus.NORMAL) {
                segments.add(new Segment(cursor, at));
            }
            cursor = at;
            current = applySeatEvent(current, event);
        }
        if (cursor.isBefore(to) && current != null && current.status == SeatStatus.NORMAL) {
            segments.add(new Segment(cursor, to));
        }
        return segments;
    }

    private SeatState applySeatEvent(SeatState current, ResourceStatusEvent event) {
        if (event.getResourceType() != ResourceType.SEAT) {
            return current;
        }
        SeatState parsed = parseSeatState(event.getAfterSnapshot());
        return parsed == null ? current : parsed;
    }

    private SeatState parseSeatState(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(snapshot);
            SeatStatus status = node.hasNonNull("status")
                    ? SeatStatus.valueOf(node.get("status").asText()) : null;
            return status == null ? null : new SeatState(status);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- segment helpers -----------------------------------------------------

    private record Segment(LocalDateTime start, LocalDateTime end) {
    }

    private record RoomState(RoomStatus status, LocalTime openTime, LocalTime closeTime) {
    }

    private record SeatState(SeatStatus status) {
    }

    private List<Segment> normalize(List<Segment> segments) {
        if (segments.isEmpty()) {
            return segments;
        }
        List<Segment> sorted = new ArrayList<>(segments);
        sorted.sort(Comparator.comparing(Segment::start).thenComparing(Segment::end));
        List<Segment> merged = new ArrayList<>();
        Segment current = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            Segment next = sorted.get(i);
            if (!next.start().isAfter(current.end())) {
                if (next.end().isAfter(current.end())) {
                    current = new Segment(current.start(), next.end());
                }
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private List<Segment> intersect(List<Segment> left, List<Segment> right) {
        List<Segment> result = new ArrayList<>();
        for (Segment a : left) {
            for (Segment b : right) {
                LocalDateTime start = latestOf(a.start(), b.start());
                LocalDateTime end = earliestOf(a.end(), b.end());
                if (start.isBefore(end)) {
                    result.add(new Segment(start, end));
                }
            }
        }
        return normalize(result);
    }

    private List<Segment> union(List<Segment> left, List<Segment> right) {
        List<Segment> all = new ArrayList<>(left);
        all.addAll(right);
        return normalize(all);
    }

    private static long secondsOf(List<Segment> segments) {
        long total = 0;
        for (Segment segment : segments) {
            total += ChronoUnit.SECONDS.between(segment.start(), segment.end());
        }
        return total;
    }

    private static LocalDateTime latestOf(LocalDateTime... values) {
        LocalDateTime max = values[0];
        for (LocalDateTime value : values) {
            if (value.isAfter(max)) {
                max = value;
            }
        }
        return max;
    }

    private static LocalDateTime earliestOf(LocalDateTime... values) {
        LocalDateTime min = values[0];
        for (LocalDateTime value : values) {
            if (value.isBefore(min)) {
                min = value;
            }
        }
        return min;
    }

    private static Double rate(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }
}
