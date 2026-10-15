package edu.campus.studyroom.rule;

import edu.campus.studyroom.rule.domain.RuleConfig;
import edu.campus.studyroom.rule.domain.RuleVersion;

/**
 * Active rule access. Reads the full version through the guard pointer,
 * never key-by-key. Publishing swaps the pointer under the guard lock.
 */
public interface RuleService {

    /** The active rule version row (guard pointer). */
    RuleVersion activeVersion();

    /** The active rule configuration. */
    RuleConfig activeRule();
}
