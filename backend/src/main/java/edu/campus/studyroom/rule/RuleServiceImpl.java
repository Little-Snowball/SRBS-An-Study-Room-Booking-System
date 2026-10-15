package edu.campus.studyroom.rule;

import edu.campus.studyroom.rule.domain.RuleConfig;
import edu.campus.studyroom.rule.domain.RuleVersion;
import edu.campus.studyroom.rule.mapper.GuardMapper;
import edu.campus.studyroom.rule.mapper.RuleVersionMapper;
import org.springframework.stereotype.Service;

/**
 * Active rule access through the guard pointer; never key-by-key.
 */
@Service
public class RuleServiceImpl implements RuleService {

    private final GuardMapper guardMapper;
    private final RuleVersionMapper ruleVersionMapper;

    public RuleServiceImpl(GuardMapper guardMapper, RuleVersionMapper ruleVersionMapper) {
        this.guardMapper = guardMapper;
        this.ruleVersionMapper = ruleVersionMapper;
    }

    @Override
    public RuleVersion activeVersion() {
        var guard = guardMapper.selectGuard();
        if (guard == null) {
            throw new edu.campus.studyroom.common.BusinessException(
                    edu.campus.studyroom.common.ErrorCode.INTERNAL_ERROR,
                    "business_guard 协调行缺失，请检查迁移 V002 是否执行");
        }
        RuleVersion version = ruleVersionMapper.selectById(guard.getActiveRuleId());
        if (version == null) {
            throw new edu.campus.studyroom.common.BusinessException(
                    edu.campus.studyroom.common.ErrorCode.INTERNAL_ERROR, "生效规则版本缺失");
        }
        return version;
    }

    @Override
    public RuleConfig activeRule() {
        return RuleConfig.fromJson(activeVersion().getConfigJson());
    }
}
