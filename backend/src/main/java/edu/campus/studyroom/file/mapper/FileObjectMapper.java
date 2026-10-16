package edu.campus.studyroom.file.mapper;

import edu.campus.studyroom.file.domain.FileObject;
import edu.campus.studyroom.file.domain.FileState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FileObjectMapper {

    FileObject selectById(@Param("id") Long id);

    FileObject selectByStorageKey(@Param("storageKey") String storageKey);

    int insert(FileObject fileObject);

    /** Conditional state change; affected rows must be 1. */
    int updateState(@Param("id") Long id,
                    @Param("expectedState") FileState expectedState,
                    @Param("state") FileState state);

    /** TEMP files not bound within the retention window (24h). */
    List<FileObject> selectExpiredTemp(@Param("before") LocalDateTime before);

    int deleteById(@Param("id") Long id);

    /** 物理删除该用户上传的全部文件记录（学生账号删除时使用，调用方保证无申诉引用）。 */
    int deleteByOwner(@Param("ownerId") Long ownerId);
}
