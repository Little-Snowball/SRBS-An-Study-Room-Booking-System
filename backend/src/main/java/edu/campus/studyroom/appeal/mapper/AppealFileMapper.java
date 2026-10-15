package edu.campus.studyroom.appeal.mapper;

import edu.campus.studyroom.appeal.domain.AppealFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppealFileMapper {

    int insert(AppealFile appealFile);

    List<AppealFile> selectByAppealId(@Param("appealId") Long appealId);

    List<AppealFile> selectByFileId(@Param("fileId") Long fileId);
}
