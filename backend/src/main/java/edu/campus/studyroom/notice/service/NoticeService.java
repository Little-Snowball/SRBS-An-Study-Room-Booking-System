package edu.campus.studyroom.notice.service;

import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.notice.domain.NoticeStatus;
import edu.campus.studyroom.notice.dto.NoticeAdminItem;
import edu.campus.studyroom.notice.dto.NoticeDetail;
import edu.campus.studyroom.notice.dto.NoticeItem;

/**
 * Notice lifecycle. Content is plain text (no HTML rendering server-side),
 * drafts are edited with a version lock, publishing/withdrawing are conditional
 * state changes, and nothing is ever physically deleted.
 */
public interface NoticeService {

    NoticeDetail createDraft(String title, String content, Boolean pinned, Long adminId);

    NoticeDetail update(Long id, String title, String content, Boolean pinned, Long expectedVersion,
                        Long adminId);

    NoticeDetail publish(Long id, Long expectedVersion, Long adminId);

    NoticeDetail withdraw(Long id, Long expectedVersion, String reason, Long adminId);

    /** Public list: PUBLISHED only, pinned first then newest published. */
    PageResult<NoticeItem> listPublished(PageQuery pageQuery);

    /** Public detail; anything not PUBLISHED is a 404. */
    NoticeItem getPublished(Long id);

    PageResult<NoticeAdminItem> adminList(NoticeStatus status, PageQuery pageQuery);
}
