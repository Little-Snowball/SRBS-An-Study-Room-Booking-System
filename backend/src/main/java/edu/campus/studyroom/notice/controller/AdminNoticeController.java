package edu.campus.studyroom.notice.controller;

import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.notice.domain.NoticeStatus;
import edu.campus.studyroom.notice.dto.CreateNoticeRequest;
import edu.campus.studyroom.notice.dto.NoticeActionRequest;
import edu.campus.studyroom.notice.dto.NoticeAdminItem;
import edu.campus.studyroom.notice.dto.NoticeDetail;
import edu.campus.studyroom.notice.dto.UpdateNoticeRequest;
import edu.campus.studyroom.notice.service.NoticeService;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin notice management. Writes run under the guard with optimistic version
 * locks; publish and withdraw are conditional state changes.
 */
@RestController
@RequestMapping("/api/v1/admin/notices")
public class AdminNoticeController {

    private final NoticeService noticeService;

    public AdminNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public ApiResponse<PageResult<NoticeAdminItem>> list(
            @RequestParam(required = false) NoticeStatus status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.ok(noticeService.adminList(status, new PageQuery(page, size, null)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoticeDetail>> create(
            @RequestBody @Valid CreateNoticeRequest request,
            @CurrentUser CurrentUserPrincipal user) {
        NoticeDetail detail = noticeService.createDraft(request.title(), request.content(),
                request.pinned(), user.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(detail));
    }

    @PatchMapping("/{id}")
    public ApiResponse<NoticeDetail> update(@PathVariable Long id,
                                            @RequestBody @Valid UpdateNoticeRequest request,
                                            @CurrentUser CurrentUserPrincipal user) {
        return ApiResponse.ok(noticeService.update(id, request.title(), request.content(),
                request.pinned(), request.expectedVersion(), user.userId()));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<NoticeDetail> publish(@PathVariable Long id,
                                             @RequestBody @Valid NoticeActionRequest request,
                                             @CurrentUser CurrentUserPrincipal user) {
        return ApiResponse.ok(noticeService.publish(id, request.expectedVersion(), user.userId()));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<NoticeDetail> withdraw(@PathVariable Long id,
                                              @RequestBody @Valid NoticeActionRequest request,
                                              @CurrentUser CurrentUserPrincipal user) {
        return ApiResponse.ok(noticeService.withdraw(id, request.expectedVersion(), request.reason(),
                user.userId()));
    }
}
