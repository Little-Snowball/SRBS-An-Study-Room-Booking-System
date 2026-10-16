package edu.campus.studyroom.common.mapper;

import edu.campus.studyroom.common.domain.CommandDedup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CommandDedupMapper {

    CommandDedup selectByKey(@Param("userId") Long userId,
                             @Param("commandType") String commandType,
                             @Param("requestKey") String requestKey);

    int insert(CommandDedup dedup);

    /** Retention cleanup: successful command records older than 7 days. */
    List<CommandDedup> selectOlderThan(@Param("before") LocalDateTime before);

    int deleteById(@Param("id") Long id);

    /** 物理删除该用户的幂等记录（学生账号删除时使用）。 */
    int deleteByUserId(@Param("userId") Long userId);
}
