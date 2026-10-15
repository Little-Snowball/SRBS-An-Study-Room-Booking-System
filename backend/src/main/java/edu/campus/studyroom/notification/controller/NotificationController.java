package edu.campus.studyroom.notification.controller;

import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.notification.dto.NotificationItem;
import edu.campus.studyroom.notification.service.NotificationServiceImpl;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的站内通知：列表（可按未读过滤、分页）与标记已读。归属校验在 Service 内完成，
 * 非本人一律 404，不泄露他人通知的存在性。
 */
@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    private final NotificationServiceImpl notificationService;

    public NotificationController(NotificationServiceImpl notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResult<NotificationItem>> list(
            @CurrentUser CurrentUserPrincipal user,
            @RequestParam(required = false) Boolean unread,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(notificationService.listForUser(user.userId(), unread, page, size));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<NotificationItem> markRead(@CurrentUser CurrentUserPrincipal user,
                                                  @PathVariable String id) {
        Long notificationId;
        try {
            notificationId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        return ApiResponse.ok(notificationService.markReadForUser(notificationId, user.userId()));
    }
}
