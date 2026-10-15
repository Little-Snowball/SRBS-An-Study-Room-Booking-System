package edu.campus.studyroom.appeal.controller;

import edu.campus.studyroom.appeal.domain.AppealStatus;
import edu.campus.studyroom.appeal.dto.AppealDetail;
import edu.campus.studyroom.appeal.dto.AppealItem;
import edu.campus.studyroom.appeal.dto.ReviewAppealRequest;
import edu.campus.studyroom.appeal.service.AppealService;
import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.IdempotencyService;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Admin appeal queue and review. Review requires an Idempotency-Key; a same-key
 * replay re-reads the current view.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminAppealController {

    private final AppealService appealService;

    public AdminAppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    @GetMapping("/appeals")
    public ApiResponse<PageResult<AppealItem>> list(
            @RequestParam(required = false) AppealStatus status,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.ok(appealService.adminList(status, account, dateFrom, dateTo,
                new PageQuery(page, size, null)));
    }

    @PostMapping("/appeals/{id}/review")
    public ApiResponse<AppealDetail> review(@PathVariable Long id,
                                            @RequestHeader("Idempotency-Key") String requestKey,
                                            @RequestBody @Valid ReviewAppealRequest request,
                                            @CurrentUser CurrentUserPrincipal user) {
        try {
            return ApiResponse.ok(appealService.review(id, request.decision(), request.comment(),
                    request.expectedVersion(), user.userId(), requestKey));
        } catch (IdempotencyService.IdempotentReplayException replay) {
            return ApiResponse.ok(appealService.getDetail(
                    Long.parseLong(replay.getResultId()), user.userId(), true));
        }
    }
}
