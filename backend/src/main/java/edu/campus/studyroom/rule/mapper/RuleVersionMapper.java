package edu.campus.studyroom.rule.mapper;

import edu.campus.studyroom.rule.domain.RuleVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RuleVersionMapper {

    RuleVersion selectById(@Param("id") Long id);

    RuleVersion selectLatest();

    Integer selectMaxVersionNo();

    List<RuleVersion> selectPage(@Param("offset") int offset, @Param("size") int size);

    long countAll();

    int insert(RuleVersion version);
}
