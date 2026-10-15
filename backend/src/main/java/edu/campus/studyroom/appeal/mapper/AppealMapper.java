package edu.campus.studyroom.appeal.mapper;

import edu.campus.studyroom.appeal.domain.Appeal;
import edu.campus.studyroom.appeal.domain.AppealFile;
import edu.campus.studyroom.appeal.domain.AppealStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AppealMapper {

    Appeal selectById(@Param("id") Long id);

    Appeal selectByViolationId(@Param("violationId") Long violationId);

    List<Appeal> selectByUserIdPage(@Param("userId") Long userId,
                                    @Param("status") AppealStatus status,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    long countByUserId(@Param("userId") Long userId, @Param("status") AppealStatus status);

    List<Appeal> selectAdminPage(@Param("status") AppealStatus status,
                                 @Param("account") String account,
                                 @Param("dateFrom") LocalDate dateFrom,
                                 @Param("dateTo") LocalDate dateTo,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countAdminPage(@Param("status") AppealStatus status,
                        @Param("account") String account,
                        @Param("dateFrom") LocalDate dateFrom,
                        @Param("dateTo") LocalDate dateTo);

    int insert(Appeal appeal);

    /** Optimistic review update; affected rows must be 1. */
    int updateReview(@Param("id") Long id,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("status") AppealStatus status,
                     @Param("reviewerId") Long reviewerId,
                     @Param("reviewComment") String reviewComment,
                     @Param("reviewedAt") java.time.LocalDateTime reviewedAt);
}
