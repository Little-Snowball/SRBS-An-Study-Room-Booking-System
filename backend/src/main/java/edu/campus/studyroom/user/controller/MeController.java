package edu.campus.studyroom.user.controller;

import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.credit.domain.CreditLogType;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import edu.campus.studyroom.user.dto.CreditLogItem;
import edu.campus.studyroom.user.dto.UserProfile;
import edu.campus.studyroom.user.dto.ViolationItem;
import edu.campus.studyroom.settlement.domain.ViolationStatus;
import edu.campus.studyroom.user.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心端点（当前登录用户）。身份来自 @CurrentUser 的数据库实时行，
 * 不接受任何用户 ID 参数；信用门槛等派生值由服务端按生效规则计算。
 */
@RestController
public class MeController {

    private final ProfileService profileService;

    public MeController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** 我的资料：身份、信用分、受限标记、黑名单标记。 */
    @GetMapping("/api/v1/me")
    public ApiResponse<UserProfile> me(@CurrentUser CurrentUserPrincipal user) {
        return ApiResponse.ok(profileService.myProfile(user.userId()));
    }

    /** 我的信用流水：type 可选（NO_SHOW/LEAVE_TIMEOUT/REWARD/APPEAL_REFUND/MANUAL_ADJUST）。 */
    @GetMapping("/api/v1/me/credits")
    public ApiResponse<PageResult<CreditLogItem>> myCredits(
            @RequestParam(required = false) String type,
            @Valid @ModelAttribute PageQuery pageQuery,
            @CurrentUser CurrentUserPrincipal user) {
        CreditLogType typeFilter = parseType(type);
        return ApiResponse.ok(profileService.myCreditLogs(user.userId(), typeFilter, pageQuery));
    }

    /** 我的处罚记录：status 可选（ACTIVE/REVOKED），含是否仍可申诉的提示标记。 */
    @GetMapping("/api/v1/me/violations")
    public ApiResponse<PageResult<ViolationItem>> myViolations(
            @RequestParam(required = false) String status,
            @Valid @ModelAttribute PageQuery pageQuery,
            @CurrentUser CurrentUserPrincipal user) {
        return ApiResponse.ok(profileService.myViolations(user.userId(), parseViolationStatus(status), pageQuery));
    }

    private static ViolationStatus parseViolationStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ViolationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.validation("status 参数不合法，须为 ACTIVE/REVOKED");
        }
    }

    private static CreditLogType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return CreditLogType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.validation(
                    "type 参数不合法，须为 NO_SHOW/LEAVE_TIMEOUT/REWARD/APPEAL_REFUND/MANUAL_ADJUST");
        }
    }
}
