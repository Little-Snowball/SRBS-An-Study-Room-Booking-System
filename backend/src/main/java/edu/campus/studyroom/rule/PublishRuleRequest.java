package edu.campus.studyroom.rule;

import edu.campus.studyroom.rule.domain.RuleConfig;
import jakarta.validation.constraints.NotBlank;

/**
 * Full-config publish request. baseVersion must match the current version.
 */
public record PublishRuleRequest(
        RuleConfig config,
        int baseVersion,
        @NotBlank String reason
) {
}
