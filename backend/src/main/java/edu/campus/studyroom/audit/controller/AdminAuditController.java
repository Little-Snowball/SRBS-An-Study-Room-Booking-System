package edu.campus.studyroom.audit.controller;

import edu.campus.studyroom.audit.domain.AuditLog;
import edu.campus.studyroom.audit.mapper.AuditLogMapper;
import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 审计日志查询（管理员）。区间上限 31 天，避免大范围扫描。
 * summary 为脱敏摘要，不含密码/令牌/现场码。
 */
@RestController
@RequestMapping("/api/v1/admin/audits")
public class AdminAuditController {

    private static final int MAX_DAYS = 31;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final AuditLogMapper auditLogMapper;
    private final BusinessClock clock;

    public AdminAuditController(AuditLogMapper auditLogMapper, BusinessClock clock) {
        this.auditLogMapper = auditLogMapper;
        this.clock = clock;
    }

    public record AuditLogItem(
            String id,
            String actorId,
            String action,
            String targetType,
            String targetId,
            String traceId,
            String createdAt
    ) {
        static AuditLogItem from(AuditLog log) {
            return new AuditLogItem(
                    String.valueOf(log.getId()),
                    log.getActorId() == null ? null : String.valueOf(log.getActorId()),
                    log.getAction(),
                    log.getTargetType(),
                    log.getTargetId(),
                    log.getTraceId(),
                    log.getCreatedAt() == null ? null
                            : log.getCreatedAt().atZone(ZONE).toOffsetDateTime().toString());
        }
    }

    @GetMapping
    public ApiResponse<PageResult<AuditLogItem>> list(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @org.springframework.web.bind.annotation.ModelAttribute PageQuery pageQuery,
            @CurrentUser CurrentUserPrincipal admin) {
        validateRange(dateFrom, dateTo);
        Long actor = parseId(actorId);
        List<AuditLog> rows = auditLogMapper.selectPage(actor, blankToNull(action), blankToNull(targetType),
                dateFrom, dateTo, pageQuery.offset(), pageQuery.size());
        long total = auditLogMapper.countPage(actor, blankToNull(action), blankToNull(targetType),
                dateFrom, dateTo);
        return ApiResponse.ok(PageResult.of(rows.stream().map(AuditLogItem::from).toList(),
                total, pageQuery.page(), pageQuery.size()));
    }

    private void validateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            return;
        }
        if (dateFrom.isAfter(dateTo)) {
            throw BusinessException.validation("dateFrom 不能晚于 dateTo");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(dateFrom, dateTo) + 1 > MAX_DAYS) {
            throw BusinessException.validation("审计查询区间上限为 " + MAX_DAYS + " 天");
        }
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw BusinessException.validation("actorId 必须为数字");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}