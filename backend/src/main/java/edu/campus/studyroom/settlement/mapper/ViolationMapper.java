package edu.campus.studyroom.settlement.mapper;

import edu.campus.studyroom.settlement.domain.Violation;
import edu.campus.studyroom.settlement.domain.ViolationStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ViolationMapper {

    Violation selectById(@Param("id") Long id);

    Violation selectByIdForUpdate(@Param("id") Long id);

    Violation selectByReservationId(@Param("reservationId") Long reservationId);

    List<Violation> selectByUserIdPage(@Param("userId") Long userId,
                                       @Param("status") ViolationStatus status,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    long countByUserId(@Param("userId") Long userId, @Param("status") ViolationStatus status);

    List<Violation> selectAdminPage(@Param("status") ViolationStatus status,
                                    @Param("account") String account,
                                    @Param("dateFrom") LocalDate dateFrom,
                                    @Param("dateTo") LocalDate dateTo,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    long countAdminPage(@Param("status") ViolationStatus status,
                        @Param("account") String account,
                        @Param("dateFrom") LocalDate dateFrom,
                        @Param("dateTo") LocalDate dateTo);

    long countActiveByUser(@Param("userId") Long userId);

    /** ACTIVE violations whose reservation starts within the week (violation-rate stats). */
    List<Violation> selectActiveInRange(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    int insert(Violation violation);

    /** Revokes an ACTIVE violation; affected rows must be 1. */
    int updateRevoked(@Param("id") Long id,
                      @Param("expectedStatus") ViolationStatus expectedStatus,
                      @Param("revokedAt") LocalDateTime revokedAt,
                      @Param("revokedBy") Long revokedBy);

    int updateDeductionLog(@Param("id") Long id, @Param("deductionLogId") Long deductionLogId);

    /** Violations already notified before the given instant (appeal window base). */
    List<Violation> selectUnnotifiedBefore(@Param("now") LocalDateTime now);
}
