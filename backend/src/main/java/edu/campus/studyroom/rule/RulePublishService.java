package edu.campus.studyroom.rule;

import edu.campus.studyroom.audit.AuditService;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.rule.domain.BusinessGuard;
import edu.campus.studyroom.rule.domain.RuleConfig;
import edu.campus.studyroom.rule.domain.RuleSource;
import edu.campus.studyroom.rule.domain.RuleVersion;
import edu.campus.studyroom.rule.mapper.GuardMapper;
import edu.campus.studyroom.rule.mapper.RuleVersionMapper;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Rule publishing. A new version is published as a complete config under the
 * guard lock; the pointer swap is immediate (no future-dated activation) and
 * historical versions stay read-only.
 */
@Service
public class RulePublishService {

    private final RuleVersionMapper ruleVersionMapper;
    private final GuardMapper guardMapper;
    private final BusinessClock clock;
    private final AuditService auditService;

    public RulePublishService(RuleVersionMapper ruleVersionMapper, GuardMapper guardMapper,
                              BusinessClock clock, AuditService auditService) {
        this.ruleVersionMapper = ruleVersionMapper;
        this.guardMapper = guardMapper;
        this.clock = clock;
        this.auditService = auditService;
    }

    /**
     * Publishes a full rule config. baseVersion must match the current version,
     * otherwise the caller is stale (409).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RuleVersion publish(CurrentUserPrincipal admin, RuleConfig config, int baseVersion, String reason) {
        guardMapper.lockGuard();
        LocalDateTime now = clock.nowLocal();

        Integer maxVersionNo = ruleVersionMapper.selectMaxVersionNo();
        int currentVersionNo = maxVersionNo == null ? 0 : maxVersionNo;
        if (baseVersion != currentVersionNo) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "规则已被其他管理员更新（当前版本 " + currentVersionNo + "），请刷新后重新发布");
        }
        String validationError = config.validationError();
        if (validationError != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, validationError);
        }

        RuleVersion version = new RuleVersion();
        version.setVersionNo(currentVersionNo + 1);
        version.setConfigJson(config.toJson());
        version.setSource(RuleSource.ADMIN_PUBLISH);
        version.setCreatedBy(admin.userId());
        version.setCreatedAt(now);
        ruleVersionMapper.insert(version);

        BusinessGuard guard = guardMapper.selectGuard();
        guard.setActiveRuleId(version.getId());
        guard.setUpdatedAt(now);
        guardMapper.updateActiveRule(guard);

        auditService.record(admin.userId(), "RULE_PUBLISH", "RULE_VERSION",
                String.valueOf(version.getId()), "{\"versionNo\":" + version.getVersionNo()
                        + ",\"reason\":\"" + safe(reason) + "\"}");
        return version;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("\"", "'").replace("\n", " ");
    }
}
