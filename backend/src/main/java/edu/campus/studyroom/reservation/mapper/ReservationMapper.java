package edu.campus.studyroom.reservation.mapper;

import edu.campus.studyroom.reservation.domain.AttendanceEvent;
import edu.campus.studyroom.reservation.domain.AttendanceEventType;
import edu.campus.studyroom.reservation.domain.Reservation;
import edu.campus.studyroom.reservation.domain.ReservationStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationMapper {

    Reservation selectById(@Param("id") Long id);

    Reservation selectByIdForUpdate(@Param("id") Long id);

    Reservation selectByOrderNo(@Param("orderNo") String orderNo);

    List<Reservation> selectByUserIdPage(@Param("userId") Long userId,
                                         @Param("status") ReservationStatus status,
                                         @Param("dateFrom") LocalDate dateFrom,
                                         @Param("dateTo") LocalDate dateTo,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    long countByUserId(@Param("userId") Long userId,
                       @Param("status") ReservationStatus status,
                       @Param("dateFrom") LocalDate dateFrom,
                       @Param("dateTo") LocalDate dateTo);

    List<Reservation> selectEffectiveByUserId(@Param("userId") Long userId);

    List<Reservation> selectEffectiveBySeatId(@Param("seatId") Long seatId);

    /** Effective reservations of all seats in a room (resource-change impact handling). */
    List<Reservation> selectEffectiveBySeatIdsOfRoom(@Param("roomId") Long roomId);

    /** Effective reservations of a seat overlapping [startAt, endAt); empty means free. */
    List<Reservation> selectOverlappingForSeat(@Param("seatId") Long seatId,
                                               @Param("startAt") LocalDateTime startAt,
                                               @Param("endAt") LocalDateTime endAt);

    /** Effective reservations of a user overlapping [startAt, endAt); empty means free. */
    List<Reservation> selectOverlappingForUser(@Param("userId") Long userId,
                                               @Param("startAt") LocalDateTime startAt,
                                               @Param("endAt") LocalDateTime endAt);

    /**
     * Settlement candidates: effective orders whose earliest applicable business
     * deadline has passed. The candidate list is never an authorization or state
     * basis; each order is re-read inside its own guarded transaction.
     */
    List<Reservation> selectSettlementCandidates(@Param("now") LocalDateTime now,
                                                 @Param("limit") int limit);

    List<Reservation> selectAdminPage(@Param("account") String account,
                                      @Param("roomId") Long roomId,
                                      @Param("status") ReservationStatus status,
                                      @Param("dateFrom") LocalDate dateFrom,
                                      @Param("dateTo") LocalDate dateTo,
                                      @Param("offset") int offset,
                                      @Param("size") int size);

    long countAdminPage(@Param("account") String account,
                        @Param("roomId") Long roomId,
                        @Param("status") ReservationStatus status,
                        @Param("dateFrom") LocalDate dateFrom,
                        @Param("dateTo") LocalDate dateTo);

    /** Count of today's reservations by start date excluding CANCELLED (stats). */
    long countByStartDate(@Param("date") LocalDate date);

    /** Distinct students with a non-cancelled reservation starting on the date (stats). */
    long countDistinctStudentByStartDate(@Param("date") LocalDate date);

    /** Cancelled reservations starting on the date (stats). */
    long countCancelledByStartDate(@Param("date") LocalDate date);

    List<Reservation> selectByStartBetween(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    int insert(Reservation reservation);

    /**
     * Optimistic state update. All mutable columns are written; the WHERE clause
     * requires the expected version, so a lost update fails with affectedRows=0.
     */
    int updateState(Reservation reservation);
}
