package edu.campus.studyroom.auth.mapper;

import edu.campus.studyroom.auth.domain.AuthSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AuthSessionMapper {

    AuthSession selectById(@Param("sid") String sid);

    int insert(AuthSession session);

    int revoke(@Param("sid") String sid, @Param("now") LocalDateTime now);

    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    long countActiveForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    List<AuthSession> selectOldestActive(@Param("userId") Long userId,
                                         @Param("now") LocalDateTime now,
                                         @Param("limit") int limit);

    int deleteExpiredBefore(@Param("before") LocalDateTime before);
}
