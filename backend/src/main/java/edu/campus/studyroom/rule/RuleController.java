package edu.campus.studyroom.rule;

import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.rule.domain.RuleVersion;
import edu.campus.studyroom.rule.mapper.RuleVersionMapper;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public display rule and admin rule management.
 */
@RestController
@RequestMapping("/api/v1")
public class RuleController {

    private final RuleService ruleService;
    private final RulePublishService rulePublishService;
    private final RuleVersionMapper ruleVersionMapper;

    public RuleController(RuleService ruleService, RulePublishService rulePublishService,
                          RuleVersionMapper ruleVersionMapper) {
        this.ruleService = ruleService;
        this.rulePublishService = rulePublishService;
        this.ruleVersionMapper = ruleVersionMapper;
    }

    /** 前端展示口径的生效规则，不泄露管理员/审计信息。 */
    @GetMapping("/rules/public")
    public ApiResponse<PublicRuleView> publicRule() {
        return ApiResponse.ok(PublicRuleView.from(ruleService.activeRule()));
    }

    @GetMapping("/admin/rules")
    public ApiResponse<AdminRuleView> activeRule(@CurrentUser CurrentUserPrincipal admin) {
        return ApiResponse.ok(AdminRuleView.from(ruleService.activeVersion()));
    }

    @GetMapping("/admin/rules/history")
    public ApiResponse<PageResult<AdminRuleView>> history(PageQuery pageQuery) {
        List<RuleVersion> versions = ruleVersionMapper.selectPage(pageQuery.offset(), pageQuery.size());
        long total = ruleVersionMapper.countAll();
        return ApiResponse.ok(PageResult.of(
                versions.stream().map(AdminRuleView::from).toList(),
                total, pageQuery.page(), pageQuery.size()));
    }

    @PostMapping("/admin/rules")
    public ApiResponse<AdminRuleView> publish(@CurrentUser CurrentUserPrincipal admin,
                                              @RequestBody @Valid PublishRuleRequest request) {
        RuleVersion version = rulePublishService.publish(admin, request.config(), request.baseVersion(),
                request.reason());
        return ApiResponse.created(AdminRuleView.from(version));
    }
}
