package edu.campus.studyroom.credit.mapper;

import edu.campus.studyroom.credit.domain.CreditAccount;
import edu.campus.studyroom.credit.domain.CreditLog;
import edu.campus.studyroom.credit.domain.CreditLogType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CreditAccountMapper {

    CreditAccount selectByUserId(@Param("userId") Long userId);

    /** Row lock for guarded credit commands. */
    CreditAccount selectByUserIdForUpdate(@Param("userId") Long userId);

    int insert(CreditAccount account);

    /** Conditional balance update; returns affected rows (must be 1). */
    int updateScore(@Param("userId") Long userId,
                    @Param("expectedVersion") Long expectedVersion,
                    @Param("newScore") int newScore,
                    @Param("now") java.time.LocalDateTime now);

    /** 物理删除信用账户（学生账号删除时使用，调用方保证无信用流水）。 */
    int deleteByUserId(@Param("userId") Long userId);
}
