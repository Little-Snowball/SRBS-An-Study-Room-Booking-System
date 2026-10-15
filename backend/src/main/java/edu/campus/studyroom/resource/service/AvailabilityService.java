package edu.campus.studyroom.resource.service;

import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.reservation.domain.PeriodStatus;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.domain.SeatCurrentStatus;
import edu.campus.studyroom.reservation.domain.TimeGrid;
import edu.campus.studyroom.reservation.mapper.ReservationMapper;
import edu.campus.studyroom.resource.domain.RoomStatus;
import edu.campus.studyroom.resource.domain.Seat;
import edu.campus.studyroom.resource.domain.SeatStatus;
import edu.campus.studyroom.resource.domain.StudyRoom;
import edu.campus.studyroom.resource.dto.AvailabilitySeatItem;
import edu.campus.studyroom.resource.dto.RoomAvailability;
import edu.campus.studyroom.resource.dto.RoomListItem;
import edu.campus.studyroom.resource.dto.RoomDetail;
import edu.campus.studyroom.resource.dto.RoomListItem;
import edu.campus.studyroom.resource.mapper.SeatMapper;
import edu.campus.studyroom.resource.mapper.StudyRoomMapper;
import edu.campus.studyroom.rule.RuleService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only availability. No guard lock. Future queries are never rejected because
 * the seat is busy now; early check-in occupancy is validated separately at check-in.
 */
@Service
public class AvailabilityService {

    private final RoomService roomService;
    private final StudyRoomMapper roomMapper;
    private final SeatMapper seatMapper;
    private final ReservationMapper reservationMapper;
    private final RuleService ruleService;
    private final BusinessClock clock;

    public AvailabilityService(RoomService roomService, StudyRoomMapper roomMapper, SeatMapper seatMapper,
                               ReservationMapper reservationMapper, RuleService ruleService,
                               BusinessClock clock) {
        this.roomService = roomService;
        this.roomMapper = roomMapper;
        this.seatMapper = seatMapper;
        this.reservationMapper = reservationMapper;
        this.ruleService = ruleService;
        this.clock = clock;
    }

    /**
     * Room list with matching seat counts. date defaults to today and must not
     * exceed today + advanceDays; startAt/endAt must be on the 30-minute grid.
     */
    public PageResult<RoomListItem> listRooms(String campus, String building, LocalDate date,
                                              LocalDateTime startAt, LocalDateTime endAt,
                                              Boolean hasPower, Boolean nearWindow, Boolean quietZone,
                                              PageQuery pageQuery) {
        int advanceDays = ruleService.activeRule().advanceDays();
        LocalDate today = clock.today();
        LocalDate queryDate = date == null ? today : date;
        if (queryDate.isBefore(today) || queryDate.isAfter(today.plusDays(advanceDays))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "仅可查询今天及未来 " + advanceDays + " 天（今天 " + today + "）");
        }
        if ((startAt == null) != (endAt == null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始与结束时间必须同时提供");
        }
        if (startAt != null) {
            validateWindow(startAt, endAt);
            if (!startAt.toLocalDate().equals(queryDate) || !endAt.toLocalDate().equals(queryDate)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "预约时段不得跨天且必须与查询日期一致");
            }
        }

        List<StudyRoom> rooms = roomMapper.selectPage(
                campus == null || campus.isBlank() ? null : campus,
                building == null || building.isBlank() ? null : building,
                null, pageQuery.offset(), pageQuery.size());
        long total = roomMapper.countPage(
                campus == null || campus.isBlank() ? null : campus,
                building == null || building.isBlank() ? null : building, null);

        List<RoomListItem> items = new ArrayList<>();
        for (StudyRoom room : rooms) {
            int matching = countMatching(room, queryDate, startAt, endAt, hasPower, nearWindow, quietZone);
            items.add(new RoomListItem(String.valueOf(room.getId()), room.getCode(), room.getName(),
                    room.getCampus(), room.getBuilding(), room.getFloorNo(),
                    room.getOpenTime(), room.getCloseTime(),
                    room.getRowsCount(), room.getColsCount(), room.getStatus(), matching));
        }
        return PageResult.of(items, total, pageQuery.page(), pageQuery.size());
    }

    private int countMatching(StudyRoom room, LocalDate date, LocalDateTime startAt, LocalDateTime endAt,
                              Boolean hasPower, Boolean nearWindow, Boolean quietZone) {
        if (room.getStatus() != RoomStatus.OPEN) {
            return 0;
        }
        List<Seat> seats;
        if (startAt != null) {
            seats = seatMapper.selectMatchingFree(room.getId(), startAt, endAt, hasPower, nearWindow, quietZone);
        } else {
            // 无时段过滤：按标签统计正常座位
            seats = seatMapper.selectByRoomId(room.getId()).stream()
                    .filter(s -> s.getStatus() == SeatStatus.NORMAL)
                    .filter(s -> hasPower == null || hasPower.equals(s.isHasPower()))
                    .filter(s -> nearWindow == null || nearWindow.equals(s.isNearWindow()))
                    .filter(s -> quietZone == null || quietZone.equals(s.isQuietZone()))
                    .toList();
        }
        return seats.size();
    }

    public RoomDetail roomDetail(Long roomId) {
        return roomService.getDetail(roomId);
    }

    /**
     * Seat grid with derived period/current statuses.
     * periodStatus: FREE/RESERVED/OCCUPIED/UNAVAILABLE for the queried window.
     * currentStatus: live status using effective_start = min(start_at, check_in_at).
     */
    public RoomAvailability availability(Long roomId, LocalDateTime startAt, LocalDateTime endAt) {
        StudyRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "自习室不存在");
        }
        validateWindow(startAt, endAt);
        LocalDateTime now = clock.nowLocal();
        LocalDate date = startAt.toLocalDate();
        LocalDateTime openAt = LocalDateTime.of(date, room.getOpenTime());
        LocalDateTime closeAt = LocalDateTime.of(date, room.getCloseTime());
        boolean withinHours = !startAt.isBefore(openAt) && !endAt.isAfter(closeAt);

        List<AvailabilitySeatItem> items = new ArrayList<>();
        for (Seat seat : seatMapper.selectByRoomId(roomId)) {
            List<Reservation> effective = reservationMapper.selectEffectiveBySeatId(seat.getId());

            Reservation overlapping = null;
            for (Reservation order : effective) {
                if (order.getStartAt().isBefore(endAt) && order.getEndAt().isAfter(startAt)) {
                    overlapping = order;
                    break;
                }
            }
            Reservation current = null;
            for (Reservation order : effective) {
                LocalDateTime effectiveStart = order.getCheckInAt() == null
                        ? order.getStartAt()
                        : (order.getCheckInAt().isBefore(order.getStartAt())
                        ? order.getCheckInAt() : order.getStartAt());
                if (!effectiveStart.isAfter(now) && now.isBefore(order.getEndAt())) {
                    current = order;
                    break;
                }
            }

            PeriodStatus period;
            SeatCurrentStatus currentStatus;
            boolean available;
            if (room.getStatus() != RoomStatus.OPEN) {
                period = PeriodStatus.UNAVAILABLE;
                currentStatus = SeatCurrentStatus.CLOSED;
                available = false;
            } else if (seat.getStatus() != SeatStatus.NORMAL) {
                period = PeriodStatus.UNAVAILABLE;
                currentStatus = SeatCurrentStatus.MAINTENANCE;
                available = false;
            } else if (!withinHours) {
                period = PeriodStatus.UNAVAILABLE;
                available = false;
                currentStatus = current == null ? SeatCurrentStatus.FREE : mapCurrent(current.getStatus());
            } else if (overlapping == null) {
                period = PeriodStatus.FREE;
                available = true;
                currentStatus = current == null ? SeatCurrentStatus.FREE : mapCurrent(current.getStatus());
            } else if (overlapping.getStatus() == ReservationStatus.RESERVED) {
                period = PeriodStatus.RESERVED;
                available = false;
                currentStatus = current == null ? SeatCurrentStatus.RESERVED : mapCurrent(current.getStatus());
            } else {
                period = PeriodStatus.OCCUPIED;
                available = false;
                currentStatus = current == null ? SeatCurrentStatus.RESERVED : mapCurrent(current.getStatus());
            }

            items.add(new AvailabilitySeatItem(String.valueOf(seat.getId()), seat.getCode(),
                    seat.getRowNo(), seat.getColNo(), seat.isHasPower(), seat.isNearWindow(),
                    seat.isQuietZone(), available, period.name(), currentStatus.name()));
        }

        return new RoomAvailability(String.valueOf(roomId), room.getRowsCount(), room.getColsCount(),
                clock.toOffset(startAt).toString(), clock.toOffset(endAt).toString(),
                clock.nowOffset().toString(), items);
    }

    private SeatCurrentStatus mapCurrent(ReservationStatus status) {
        return switch (status) {
            case RESERVED -> SeatCurrentStatus.RESERVED;
            case IN_USE -> SeatCurrentStatus.IN_USE;
            case LEFT_TEMP -> SeatCurrentStatus.LEFT_TEMP;
            default -> SeatCurrentStatus.FREE;
        };
    }

    private void validateWindow(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始与结束时间必填");
        }
        if (!startAt.isBefore(endAt)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结束时间必须晚于开始时间");
        }
        int slot = ruleService.activeRule().slotMinutes();
        if (!TimeGrid.isOnGrid(startAt, slot) || !TimeGrid.isOnGrid(endAt, slot)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_GRID, "起止时间必须为整点或半点");
        }
        if (!startAt.toLocalDate().equals(endAt.toLocalDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "预约时段不得跨天");
        }
    }

    /** 营业时间解析助手（供控制器校验）。 */
    public static LocalTime parseTime(String value) {
        return LocalTime.parse(value);
    }
}
