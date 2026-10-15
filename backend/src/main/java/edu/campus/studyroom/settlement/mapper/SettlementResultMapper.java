package edu.campus.studyroom.settlement.mapper;

import edu.campus.studyroom.settlement.domain.SettlementResult;
import edu.campus.studyroom.settlement.domain.Violation;
import edu.campus.studyroom.settlement.domain.ViolationStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SettlementResultMapper {

    SettlementResult selectByReservationId(@Param("reservationId") Long reservationId);

    int insert(SettlementResult result);

    List<SettlementResult> selectByReservationIds(@Param("reservationIds") List<Long> reservationIds);
}
