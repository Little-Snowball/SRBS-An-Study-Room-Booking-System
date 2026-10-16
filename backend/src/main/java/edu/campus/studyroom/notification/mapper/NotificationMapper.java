package edu.campus.studyroom.notification.mapper;

import edu.campus.studyroom.notification.domain.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationMapper {

    Notification selectById(@Param("id") Long id);

    /** Idempotency pre-check for publish (UQ on event_key). */
    Notification selectByEventKey(@Param("eventKey") String eventKey);

    List<Notification> selectByUserIdPage(@Param("userId") Long userId,
                                          @Param("unread") Boolean unread,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    long countByUserId(@Param("userId") Long userId, @Param("unread") Boolean unread);

    long countUnread(@Param("userId") Long userId);

    int insert(Notification notification);

    /** Marks read for the owner when still unread; affected rows 0 or 1. */
    int markRead(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("readAt") LocalDateTime readAt);

    /** 物理删除该用户的全部通知（学生账号删除时使用）。 */
    int deleteByUserId(@Param("userId") Long userId);
}
