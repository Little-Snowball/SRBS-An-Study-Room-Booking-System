package edu.campus.studyroom.appeal.controller;

import edu.campus.studyroom.appeal.domain.AppealStatus;
import edu.campus.studyroom.appeal.dto.AppealDetail;
import edu.campus.studyroom.appeal.dto.AppealItem;
import edu.campus.studyroom.appeal.dto.SubmitAppealRequest;
import edu.campus.studyroom.appeal.service.AppealService;
import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.IdempotencyService;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Student-facing appeal endpoints. Submission requires an Idempotency-Key;
 * a same-key replay re-reads the current view under the caller's permission.
 */
@RestController
@RequestMapping("/api/v1")
public class AppealController {

    private final AppealService appealService;

    public AppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    @PostMapping("/appeals")
    public ResponseEntity<ApiResponse<AppealDetail>> submit(
            @RequestHeader("Idempotency-Key") String requestKey,
            @RequestBody @Valid SubmitAppealRequest request,
            @CurrentUser CurrentUserPrincipal user) {
        try {
            AppealDetail detail = appealService.submit(user.userId(), request.violationId(),
                    request.reason(), request.fileIds(), requestKey);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(detail));
        } catch (IdempotencyService.IdempotentReplayException replay) {
            AppealDetail detail = appealService.getDetail(
                    Long.parseLong(replay.getResultId()), user.userId(), false);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(detail));
        }
    }

    @GetMapping("/me/appeals")
    public ApiResponse<PageResult<AppealItem>> listMine(
            @RequestParam(required = false) AppealStatus status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @CurrentUser CurrentUserPrincipal user) {
        return ApiResponse.ok(appealService.listMine(user.userId(), status, new PageQuery(page, size, null)));
    }

    @GetMapping("/appeals/{id}")
    public ApiResponse<AppealDetail> detail(@PathVariable Long id,
                                            @CurrentUser CurrentUserPrincipal user) {
        return ApiResponse.ok(appealService.getDetail(id, user.userId(), user.isAdmin()));
    }
}
