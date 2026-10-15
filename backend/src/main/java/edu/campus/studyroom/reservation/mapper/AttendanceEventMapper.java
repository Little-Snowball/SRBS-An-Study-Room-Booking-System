package edu.campus.studyroom.reservation.mapper;

import edu.campus.studyroom.reservation.domain.AttendanceEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AttendanceEventMapper {

    AttendanceEvent selectByReservationAndRequestKey(@Param("reservationId") Long reservationId,
                                                     @Param("requestKey") String requestKey);

    List<AttendanceEvent> selectByReservationId(@Param("reservationId") Long reservationId);

    /** CHECK_IN events with event_at inside [from, to) for the traffic stats. */
    List<AttendanceEvent> selectCheckInsBetween(@Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to,
                                                @Param("roomId") Long roomId);

    int insert(AttendanceEvent event);

    long countByReservation(@Param("reservationId") Long reservationId);
}
