package edu.campus.studyroom.resource.mapper;

import edu.campus.studyroom.resource.domain.RetainedService;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RetainedServiceMapper {

    int insert(RetainedService retainedService);

    List<RetainedService> selectByReservationId(@Param("reservationId") Long reservationId);

    /** Retained intervals overlapping [from, to) for the given seats (stats union). */
    List<RetainedService> selectBySeatIdsBetween(@Param("seatIds") List<Long> seatIds,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);
}
