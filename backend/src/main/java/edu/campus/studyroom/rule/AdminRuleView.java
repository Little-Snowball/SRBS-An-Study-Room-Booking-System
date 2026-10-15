package edu.campus.studyroom.rule;

import edu.campus.studyroom.rule.domain.RuleConfig;
import edu.campus.studyroom.rule.domain.RuleVersion;

import java.time.ZoneId;

/**
 * Admin view of a rule version (full config, read-only history).
 */
public record AdminRuleView(
        String id,
        int versionNo,
        String source,
        String createdBy,
        String createdAt,
        RuleConfig config
) {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static AdminRuleView from(RuleVersion version) {
        return new AdminRuleView(
                String.valueOf(version.getId()),
                version.getVersionNo(),
                version.getSource().name(),
                version.getCreatedBy() == null ? null : String.valueOf(version.getCreatedBy()),
                version.getCreatedAt() == null ? null
                        : version.getCreatedAt().atZone(ZONE).toOffsetDateTime().toString(),
                RuleConfig.fromJson(version.getConfigJson()));
    }
}
