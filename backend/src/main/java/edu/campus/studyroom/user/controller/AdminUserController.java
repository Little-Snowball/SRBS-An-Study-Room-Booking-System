package edu.campus.studyroom.user.controller;

import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import edu.campus.studyroom.user.domain.UserRole;
import edu.campus.studyroom.user.domain.UserStatus;
import edu.campus.studyroom.user.dto.AdminUserItem;
import edu.campus.studyroom.user.dto.CreateStudentRequest;
import edu.campus.studyroom.user.dto.CreateStudentResponse;
import edu.campus.studyroom.user.dto.CreditAdjustRequest;
import edu.campus.studyroom.user.dto.CreditAdjustResponse;
import edu.campus.studyroom.user.dto.OneTimePasswordResponse;
import edu.campus.studyroom.user.dto.ResetPasswordRequest;
import edu.campus.studyroom.user.dto.UpdateStudentRequest;
import edu.campus.studyroom.user.dto.UserBlockRequest;
import edu.campus.studyroom.user.dto.UserStatusRequest;
import edu.campus.studyroom.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员账号管理端点。/admin/** 已由 SecurityConfig 限定 ROLE_ADMIN，
 * 控制器内再做一次角色校验（纵深防御）；服务端另校验目标必须是学生账号，
 * 且管理员不能操作自己或其他管理员。
 * <p>reset-password 与 credit-adjust 要求 Idempotency-Key 头：同键重放不重复
 * 生效（重置密码不返回二次明文，信用调整按原流水值重放）。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    /** 账号分页：account 前缀模糊，role/status 可选过滤。本页是学生账号管理，默认只返回 STUDENT（管理员不出现）。 */
    @GetMapping
    public ApiResponse<PageResult<AdminUserItem>> page(
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @Valid @ModelAttribute PageQuery pageQuery,
            @CurrentUser CurrentUserPrincipal admin) {
        requireAdmin(admin);
        UserRole requestedRole = parseRole(role);
        return ApiResponse.ok(userService.pageUsers(
                account, requestedRole == null ? UserRole.STUDENT : requestedRole, parseStatus(status), pageQuery));
    }

    /** 创建学生账号：201，响应体含一次性密码（仅本次可见）。 */
    @PostMapping
    public ResponseEntity<ApiResponse<CreateStudentResponse>> create(
            @Valid @RequestBody CreateStudentRequest request,
            @CurrentUser CurrentUserPrincipal admin) {
        requireAdmin(admin);
        CreateStudentResponse created = userService.createStudent(admin, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(created));
    }

    /** 修改学生身份信息（学号/姓名/学院）。学号查重 409；学号变更会使该学生会话失效。 */
    @PatchMapping("/{id}")
    public ApiResponse<AdminUserItem> update(@PathVariable String id,
                                             @Valid @RequestBody UpdateStudentRequest request,
                                             @CurrentUser CurrentUserPrincipal admin) {
        requireAdmin(admin);
        return ApiResponse.ok(userService.updateStudent(admin, parseId(id, "用户 ID"), request));
    }

    /** 删除学生账号：仅无业务历史（预约/违约/流水/申诉）时可删，否则 409。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id,
                                    @CurrentUser CurrentUserPrincipal admin) {
        requireAdmin(admin);
        userService.deleteStudent(admin, parseId(id, "用户 ID"));
        return ApiResponse.ok(null);
    }

    /** 启用/禁用：禁用且有有效订单时需 confirmTerminate，否则 422 说明笔数。 */
    @PostMapping("/{id}/status")
    public ApiResponse<AdminUserItem> updateStatus(@PathVariable String id,
                                                   @Valid @RequestBody UserStatusRequest request,
                                                   @CurrentUser CurrentUserPrincipal admin) {
        requireAdmin(admin);
        return ApiResponse.ok(userService.setUserStatus(admin, parseId(id, "用户 ID"), request));
    }

    /** 人工黑名单开关：拉黑同时无责终止有效订单并撤销会话。 */
    @PostMapping("/{id}/block")
    public ApiResponse<AdminUserItem> updateBlock(@PathVariable String id,
                                                  @Valid @RequestBody UserBlockRequest request,
                                                  @CurrentUser CurrentUserPrincipal admin) {
        requireAdmin(admin);
        return ApiResponse.ok(userService.setUserBlocked(admin, parseId(id, "用户 ID"), request));
    }

    /** 重置密码：一次性密码仅首次响应返回；同键重放仅告知凭证已下发。 */
    @PostMapping("/{id}/reset-password")
    public ApiResponse<OneTimePasswordResponse> resetPassword(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ResetPasswordRequest request,
            @CurrentUser CurrentUserPrincipal admin) {
        requireAdmin(admin);
        return ApiResponse.ok(
                userService.resetPassword(admin, parseId(id, "用户 ID"), request, idempotencyKey));
    }

    /** 人工信用调整：目标分 0—100，走信用唯一入口，同键重放按原流水值重放。 */
    @PostMapping("/{id}/credit-adjust")
    public ApiResponse<CreditAdjustResponse> adjustCredit(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreditAdjustRequest request,
            @CurrentUser CurrentUserPrincipal admin) {
        requireAdmin(admin);
        return ApiResponse.ok(
                userService.adjustCredit(admin, parseId(id, "用户 ID"), request, idempotencyKey));
    }

    private static void requireAdmin(CurrentUserPrincipal admin) {
        if (admin == null || !admin.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可以执行该操作");
        }
    }

    private static Long parseId(String raw, String label) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw BusinessException.validation(label + " 不合法");
        }
    }

    private static UserRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.validation("role 参数不合法，须为 STUDENT/ADMIN");
        }
    }

    private static UserStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.validation("status 参数不合法，须为 ACTIVE/DISABLED");
        }
    }
}
