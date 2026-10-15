package edu.campus.studyroom.notice.service;

import edu.campus.studyroom.audit.AuditService;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.notice.domain.Notice;
import edu.campus.studyroom.notice.domain.NoticeStatus;
import edu.campus.studyroom.notice.dto.NoticeAdminItem;
import edu.campus.studyroom.notice.dto.NoticeDetail;
import edu.campus.studyroom.notice.dto.NoticeItem;
import edu.campus.studyroom.notice.mapper.NoticeMapper;
import edu.campus.studyroom.rule.BusinessGuardLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Guarded management writes with optimistic version locks; public reads are
 * plain queries. Plain text is stored verbatim; the frontend escapes on render.
 */
@Service
public class NoticeServiceImpl implements NoticeService {

    private final NoticeMapper noticeMapper;
    private final BusinessGuardLock businessGuardLock;
    private final AuditService auditService;
    private final BusinessClock clock;

    public NoticeServiceImpl(NoticeMapper noticeMapper, BusinessGuardLock businessGuardLock,
                             AuditService auditService, BusinessClock clock) {
        this.noticeMapper = noticeMapper;
        this.businessGuardLock = businessGuardLock;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NoticeDetail createDraft(String title, String content, Boolean pinned, Long adminId) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        Notice notice = new Notice();
        notice.setTitle(validateTitle(title));
        notice.setContent(validateContent(content));
        notice.setPinned(Boolean.TRUE.equals(pinned));
        notice.setStatus(NoticeStatus.DRAFT);
        notice.setCreatedBy(adminId);
        notice.setVersion(0L);
        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);
        noticeMapper.insert(notice);
        auditService.record(adminId, "NOTICE_CREATE", "NOTICE", String.valueOf(notice.getId()),
                "{\"titleLength\":" + notice.getTitle().length() + "}");
        return adminDetail(noticeMapper.selectById(notice.getId()));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NoticeDetail update(Long id, String title, String content, Boolean pinned,
                               Long expectedVersion, Long adminId) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw BusinessException.notFound("公告不存在");
        }
        if (!notice.getVersion().equals(expectedVersion)) {
            throw BusinessException.invalidState("公告版本已变更，请刷新后重试");
        }
        Notice update = new Notice();
        update.setId(id);
        update.setTitle(validateTitle(title));
        update.setContent(validateContent(content));
        update.setPinned(Boolean.TRUE.equals(pinned));
        update.setVersion(expectedVersion);
        update.setUpdatedAt(now);
        int updated = noticeMapper.update(update);
        if (updated != 1) {
            throw BusinessException.invalidState("公告版本已变更，请刷新后重试");
        }
        auditService.record(adminId, "NOTICE_UPDATE", "NOTICE", String.valueOf(id),
                "{\"titleLength\":" + update.getTitle().length() + "}");
        return adminDetail(noticeMapper.selectById(id));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NoticeDetail publish(Long id, Long expectedVersion, Long adminId) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw BusinessException.notFound("公告不存在");
        }
        if (!notice.getVersion().equals(expectedVersion)) {
            throw BusinessException.invalidState("公告版本已变更，请刷新后重试");
        }
        if (notice.getStatus() == NoticeStatus.PUBLISHED) {
            // Already published: a repeat reports the current state.
            return adminDetail(notice);
        }
        int updated = noticeMapper.updateStatus(id, expectedVersion, NoticeStatus.PUBLISHED, now, now);
        if (updated != 1) {
            throw BusinessException.invalidState("公告版本已变更，请刷新后重试");
        }
        auditService.record(adminId, "NOTICE_PUBLISH", "NOTICE", String.valueOf(id),
                "{\"status\":\"PUBLISHED\"}");
        return adminDetail(noticeMapper.selectById(id));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NoticeDetail withdraw(Long id, Long expectedVersion, String reason, Long adminId) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw BusinessException.notFound("公告不存在");
        }
        if (!notice.getVersion().equals(expectedVersion)) {
            throw BusinessException.invalidState("公告版本已变更，请刷新后重试");
        }
        if (notice.getStatus() == NoticeStatus.WITHDRAWN) {
            // Already withdrawn: a repeat reports the current state.
            return adminDetail(notice);
        }
        if (notice.getStatus() == NoticeStatus.DRAFT) {
            throw BusinessException.invalidState("草稿尚未发布，无需撤回");
        }
        int updated = noticeMapper.updateStatus(id, expectedVersion, NoticeStatus.WITHDRAWN, null, now);
        if (updated != 1) {
            throw BusinessException.invalidState("公告版本已变更，请刷新后重试");
        }
        auditService.record(adminId, "NOTICE_WITHDRAW", "NOTICE", String.valueOf(id),
                "{\"status\":\"WITHDRAWN\",\"reasonLength\":"
                        + (reason == null ? 0 : reason.trim().length()) + "}");
        return adminDetail(noticeMapper.selectById(id));
    }

    @Override
    public PageResult<NoticeItem> listPublished(PageQuery pageQuery) {
        List<Notice> notices = noticeMapper.selectPublishedPage(pageQuery.offset(), pageQuery.size());
        long total = noticeMapper.countPublished();
        List<NoticeItem> items = new ArrayList<>(notices.size());
        for (Notice notice : notices) {
            items.add(new NoticeItem(notice.getId(), notice.getTitle(), notice.getContent(),
                    Boolean.TRUE.equals(notice.isPinned()), clock.toOffset(notice.getPublishedAt())));
        }
        return PageResult.of(items, total, pageQuery.page(), pageQuery.size());
    }

    @Override
    public NoticeItem getPublished(Long id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null || notice.getStatus() != NoticeStatus.PUBLISHED) {
            throw BusinessException.notFound("公告不存在或未发布");
        }
        return new NoticeItem(notice.getId(), notice.getTitle(), notice.getContent(),
                Boolean.TRUE.equals(notice.isPinned()), clock.toOffset(notice.getPublishedAt()));
    }

    @Override
    public PageResult<NoticeAdminItem> adminList(NoticeStatus status, PageQuery pageQuery) {
        List<Notice> notices = noticeMapper.selectAdminPage(status, pageQuery.offset(), pageQuery.size());
        long total = noticeMapper.countAdmin(status);
        List<NoticeAdminItem> items = new ArrayList<>(notices.size());
        for (Notice notice : notices) {
            items.add(new NoticeAdminItem(notice.getId(), notice.getTitle(), notice.getStatus(),
                    Boolean.TRUE.equals(notice.isPinned()), clock.toOffset(notice.getPublishedAt()),
                    clock.toOffset(notice.getUpdatedAt())));
        }
        return PageResult.of(items, total, pageQuery.page(), pageQuery.size());
    }

    private NoticeDetail adminDetail(Notice notice) {
        return new NoticeDetail(notice.getId(), notice.getTitle(), notice.getContent(),
                Boolean.TRUE.equals(notice.isPinned()), notice.getStatus(),
                clock.toOffset(notice.getPublishedAt()), notice.getCreatedBy(), notice.getVersion(),
                clock.toOffset(notice.getCreatedAt()), clock.toOffset(notice.getUpdatedAt()));
    }

    private static String validateTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) {
            throw BusinessException.validation("标题不能为空");
        }
        if (normalized.length() > 100) {
            throw BusinessException.validation("标题不能超过 100 字");
        }
        return normalized;
    }

    private static String validateContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            throw BusinessException.validation("内容不能为空");
        }
        if (normalized.length() > 5000) {
            throw BusinessException.validation("内容不能超过 5000 字");
        }
        return normalized;
    }
}
