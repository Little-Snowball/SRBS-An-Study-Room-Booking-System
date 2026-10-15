package edu.campus.studyroom.notice.mapper;

import edu.campus.studyroom.notice.domain.Notice;
import edu.campus.studyroom.notice.domain.NoticeStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoticeMapper {

    Notice selectById(@Param("id") Long id);

    List<Notice> selectPublishedPage(@Param("offset") int offset, @Param("size") int size);

    long countPublished();

    List<Notice> selectAdminPage(@Param("status") NoticeStatus status,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countAdmin(@Param("status") NoticeStatus status);

    int insert(Notice notice);

    int update(Notice notice);

    int updateStatus(@Param("id") Long id,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("status") NoticeStatus status,
                     @Param("publishedAt") java.time.LocalDateTime publishedAt,
                     @Param("now") java.time.LocalDateTime now);
}
