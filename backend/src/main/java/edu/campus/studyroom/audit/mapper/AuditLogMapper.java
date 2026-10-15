package edu.campus.studyroom.audit.mapper;

import edu.campus.studyroom.audit.domain.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AuditLogMapper {

    int insert(AuditLog auditLog);

    List<AuditLog> selectPage(@Param("actorId") Long actorId,
                              @Param("action") String action,
                              @Param("targetType") String targetType,
                              @Param("dateFrom") LocalDate dateFrom,
                              @Param("dateTo") LocalDate dateTo,
                              @Param("offset") int offset,
                              @Param("size") int size);

    long countPage(@Param("actorId") Long actorId,
                   @Param("action") String action,
                   @Param("targetType") String targetType,
                   @Param("dateFrom") LocalDate dateFrom,
                   @Param("dateTo") LocalDate dateTo);
}
