package edu.campus.studyroom.audit;

import edu.campus.studyroom.audit.domain.AuditLog;
import edu.campus.studyroom.audit.mapper.AuditLogMapper;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Audit writes. A failed audit insert fails the whole management transaction;
 * summaries are sanitized here so passwords, tokens, check-in codes and file
 * contents can never reach the database.
 */
@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final BusinessClock clock;

    public AuditService(AuditLogMapper auditLogMapper, BusinessClock clock) {
        this.auditLogMapper = auditLogMapper;
        this.clock = clock;
    }

    /** Records an audit row inside the current transaction (MANDATORY). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, String action, String targetType, String targetId, String summaryJson) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(String.valueOf(targetId));
        String traceId = MDC.get("traceId");
        log.setTraceId(traceId == null ? "" : traceId);
        log.setSummary(summaryJson);
        log.setCreatedAt(LocalDateTime.now(clock.zone()));
        auditLogMapper.insert(log);
    }

    public void record(CurrentUserPrincipal actor, String action, String targetType, String targetId,
                       String summaryJson) {
        record(actor == null ? null : actor.userId(), action, targetType, targetId, summaryJson);
    }
}
