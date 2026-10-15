package edu.campus.studyroom.notice.controller;

import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.notice.dto.NoticeItem;
import edu.campus.studyroom.notice.service.NoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public notice reads (whitelisted in SecurityConfig). Only PUBLISHED notices
 * are listed; anything else is a 404 on detail.
 */
@RestController
@RequestMapping("/api/v1/notices")
public class PublicNoticeController {

    private final NoticeService noticeService;

    public PublicNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public ApiResponse<PageResult<NoticeItem>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.ok(noticeService.listPublished(new PageQuery(page, size, null)));
    }

    @GetMapping("/{id}")
    public ApiResponse<NoticeItem> detail(@PathVariable Long id) {
        return ApiResponse.ok(noticeService.getPublished(id));
    }
}
