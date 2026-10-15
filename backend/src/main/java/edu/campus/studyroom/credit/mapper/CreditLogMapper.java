package edu.campus.studyroom.credit.mapper;

import edu.campus.studyroom.credit.domain.CreditLog;
import edu.campus.studyroom.credit.domain.CreditLogType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CreditLogMapper {

    CreditLog selectById(@Param("id") Long id);

    CreditLog selectByEventKey(@Param("eventKey") String eventKey);

    List<CreditLog> selectByUserIdPage(@Param("userId") Long userId,
                                       @Param("type") CreditLogType type,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    long countByUserId(@Param("userId") Long userId, @Param("type") CreditLogType type);

    List<CreditLog> selectByReservationId(@Param("reservationId") Long reservationId);

    int insert(CreditLog log);
}
