package edu.campus.studyroom.appeal.service;

import edu.campus.studyroom.appeal.domain.AppealStatus;
import edu.campus.studyroom.appeal.dto.AppealDetail;
import edu.campus.studyroom.appeal.dto.AppealItem;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;

import java.time.LocalDate;
import java.util.List;

/**
 * Violation appeals. A violation accepts exactly one ticket; a REJECTED appeal
 * cannot be resubmitted. APPROVE revokes the violation and refunds the deduction
 * through the single credit entry point; the order itself never revives.
 */
public interface AppealService {

    /** Submits an appeal for the caller's own ACTIVE violation inside the 48h window. */
    AppealDetail submit(Long userId, Long violationId, String reason, List<Long> fileIds, String requestKey);

    /** The caller's appeals, newest first. */
    PageResult<AppealItem> listMine(Long userId, AppealStatus status, PageQuery pageQuery);

    /** One appeal; ownership is enforced unless the viewer is an admin. */
    AppealDetail getDetail(Long appealId, Long userId, boolean isAdmin);

    /** Admin queue filtered by status, account and submitted-date range. */
    PageResult<AppealItem> adminList(AppealStatus status, String account, LocalDate dateFrom,
                                     LocalDate dateTo, PageQuery pageQuery);

    /**
     * Reviews a PENDING appeal. A repeat of the same decision returns the
     * existing outcome; the opposite decision on a finished appeal conflicts.
     */
    AppealDetail review(Long appealId, String decision, String comment, Long expectedVersion,
                        Long reviewerId, String requestKey);
}
