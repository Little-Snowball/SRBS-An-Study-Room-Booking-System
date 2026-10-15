package edu.campus.studyroom.user.mapper;

import edu.campus.studyroom.user.domain.AppUser;
import edu.campus.studyroom.user.domain.UserRole;
import edu.campus.studyroom.user.domain.UserStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppUserMapper {

    AppUser selectById(@Param("id") Long id);

    AppUser selectByAccount(@Param("account") String account);

    /** Row lock for guarded commands. */
    AppUser selectByIdForUpdate(@Param("id") Long id);

    List<AppUser> selectPage(@Param("account") String account,
                             @Param("role") UserRole role,
                             @Param("status") UserStatus status,
                             @Param("offset") int offset,
                             @Param("size") int size);

    long countPage(@Param("account") String account,
                   @Param("role") UserRole role,
                   @Param("status") UserStatus status);

    int insert(AppUser user);

    int update(AppUser user);

    /** Conditional status change; returns affected rows (must be 1). */
    int updateStatus(@Param("id") Long id,
                     @Param("expectedAuthVersion") Integer expectedAuthVersion,
                     @Param("status") UserStatus status,
                     @Param("blocked") Boolean blocked,
                     @Param("now") LocalDateTime now);

    int bumpAuthVersion(@Param("id") Long id, @Param("now") LocalDateTime now);

    int updatePassword(@Param("id") Long id,
                       @Param("passwordHash") String passwordHash,
                       @Param("mustChangePassword") boolean mustChangePassword,
                       @Param("now") LocalDateTime now);

    long countByRole(UserRole role);
}
