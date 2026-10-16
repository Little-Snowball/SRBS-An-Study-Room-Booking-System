package edu.campus.studyroom.rule;

import edu.campus.studyroom.rule.domain.RuleConfig;
import jakarta.validation.constraints.Size;

/**
 * Full-config publish request. baseVersion must match the current version.
 * The reason is optional and only recorded for the audit trail.
 */
public record PublishRuleRequest(
        RuleConfig config,
        int baseVersion,
        @Size(max = 500, message = "操作原因不能超过 500 字") String reason
) {
}
