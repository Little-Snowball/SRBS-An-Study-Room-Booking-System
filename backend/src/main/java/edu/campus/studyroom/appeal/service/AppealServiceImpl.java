package edu.campus.studyroom.appeal.service;

import edu.campus.studyroom.appeal.domain.Appeal;
import edu.campus.studyroom.appeal.domain.AppealFile;
import edu.campus.studyroom.appeal.domain.AppealStatus;
import edu.campus.studyroom.appeal.dto.AppealDetail;
import edu.campus.studyroom.appeal.dto.AppealItem;
import edu.campus.studyroom.appeal.mapper.AppealFileMapper;
import edu.campus.studyroom.appeal.mapper.AppealMapper;
import edu.campus.studyroom.audit.AuditService;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.IdempotencyService;
import edu.campus.studyroom.common.Ids;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.credit.CreditCommand;
import edu.campus.studyroom.credit.domain.CreditLogType;
import edu.campus.studyroom.credit.CreditService;
import edu.campus.studyroom.credit.domain.CreditLog;
import edu.campus.studyroom.credit.mapper.CreditLogMapper;
import edu.campus.studyroom.file.service.FileService;
import edu.campus.studyroom.notification.NotificationService;
import edu.campus.studyroom.rule.BusinessGuardLock;
import edu.campus.studyroom.settlement.domain.Violation;
import edu.campus.studyroom.settlement.domain.ViolationStatus;
import edu.campus.studyroom.settlement.mapper.ViolationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Guarded, idempotent appeal commands. Submitting binds already-uploaded TEMP
 * evidence inside the same transaction; approving revokes the violation and
 * refunds the original deduction with one idempotent credit event.
 */
@Service
public class AppealServiceImpl implements AppealService {

    private static final int APPEAL_WINDOW_HOURS = 48;
    private static final int ADMIN_RANGE_MAX_DAYS = 31;
    private static final String COMMAND_SUBMIT = "APPEAL_SUBMIT";
    private static final String COMMAND_REVIEW = "APPEAL_REVIEW";

    private final AppealMapper appealMapper;
    private final AppealFileMapper appealFileMapper;
    private final ViolationMapper violationMapper;
    private final CreditLogMapper creditLogMapper;
    private final CreditService creditService;
    private final NotificationService notificationService;
    private final FileService fileService;
    private final BusinessGuardLock businessGuardLock;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final BusinessClock clock;

    public AppealServiceImpl(AppealMapper appealMapper, AppealFileMapper appealFileMapper,
                             ViolationMapper violationMapper, CreditLogMapper creditLogMapper,
                             CreditService creditService, NotificationService notificationService,
                             FileService fileService, BusinessGuardLock businessGuardLock,
                             IdempotencyService idempotencyService, AuditService auditService,
                             BusinessClock clock) {
        this.appealMapper = appealMapper;
        this.appealFileMapper = appealFileMapper;
        this.violationMapper = violationMapper;
        this.creditLogMapper = creditLogMapper;
        this.creditService = creditService;
        this.notificationService = notificationService;
        this.fileService = fileService;
        this.businessGuardLock = businessGuardLock;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppealDetail submit(Long userId, Long violationId, String reason, List<Long> fileIds,
                               String requestKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();

        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.length() < 10 || normalizedReason.length() > 1000) {
            throw BusinessException.validation("申诉理由须为 10—1000 字");
        }
        List<Long> uniqueFileIds = fileIds == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(fileIds));
        String requestHash = Ids.sha256Hex(violationId + "|" + normalizedReason + "|" + uniqueFileIds);
        idempotencyService.checkInTransaction(userId, COMMAND_SUBMIT, requestKey, requestHash);

        Violation violation = violationMapper.selectById(violationId);
        if (violation == null || !violation.getUserId().equals(userId)
                || violation.getStatus() != ViolationStatus.ACTIVE) {
            throw BusinessException.notFound("违约记录不存在或当前不可申诉");
        }
        // 48h window from the violation notification, deadline point included.
        LocalDateTime deadline = violation.getNotifiedAt().plusHours(APPEAL_WINDOW_HOURS);
        if (now.isAfter(deadline)) {
            throw new BusinessException(ErrorCode.APPEAL_EXPIRED,
                    "申诉窗口已截止：需在违约通知后 48 小时内提交（含截止点）");
        }
        Appeal existing = appealMapper.selectByViolationId(violationId);
        if (existing != null) {
            throw switch (existing.getStatus()) {
                case PENDING -> new BusinessException(ErrorCode.APPEAL_EXISTS, "该处罚已有待处理的申诉");
                case APPROVED -> new BusinessException(ErrorCode.APPEAL_EXISTS, "该处罚的申诉已审核通过");
                case REJECTED -> new BusinessException(ErrorCode.APPEAL_EXISTS,
                        "该处罚的申诉已被驳回，不允许重复提交，请联系管理员");
            };
        }

        Appeal appeal = new Appeal();
        appeal.setViolationId(violationId);
        appeal.setUserId(userId);
        appeal.setReason(normalizedReason);
        appeal.setStatus(AppealStatus.PENDING);
        appeal.setSubmittedAt(now);
        appeal.setVersion(0L);
        appealMapper.insert(appeal);

        for (Long fileId : uniqueFileIds) {
            fileService.bindToAppeal(fileId, appeal.getId(), userId);
            AppealFile appealFile = new AppealFile();
            appealFile.setAppealId(appeal.getId());
            appealFile.setFileId(fileId);
            appealFileMapper.insert(appealFile);
        }

        idempotencyService.recordSuccess(userId, COMMAND_SUBMIT, requestKey, requestHash,
                "APPEAL", String.valueOf(appeal.getId()));
        auditService.record(userId, "APPEAL_SUBMIT", "APPEAL", String.valueOf(appeal.getId()),
                "{\"violationId\":" + violationId + ",\"fileCount\":" + uniqueFileIds.size()
                        + ",\"reasonLength\":" + normalizedReason.length() + "}");
        return toDetail(appeal);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppealDetail review(Long appealId, String decision, String comment, Long expectedVersion,
                               Long reviewerId, String requestKey) {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();

        String normalizedDecision = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        // 前端状态机使用 APPROVED/REJECTED，历史接口使用 APPROVE/REJECT，两者均接受
        boolean approve = "APPROVE".equals(normalizedDecision) || "APPROVED".equals(normalizedDecision);
        boolean reject = "REJECT".equals(normalizedDecision) || "REJECTED".equals(normalizedDecision);
        if (!approve && !reject) {
            throw BusinessException.validation("decision 必须为 APPROVE(APPROVED) 或 REJECT(REJECTED)");
        }
        String normalizedComment = comment == null ? "" : comment.trim();
        if (normalizedComment.length() > 1000) {
            throw BusinessException.validation("审核意见不能超过 1000 字");
        }

        Appeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw BusinessException.notFound("申诉不存在");
        }
        String requestHash = Ids.sha256Hex(appealId + "|" + normalizedDecision + "|"
                + normalizedComment + "|" + expectedVersion);
        idempotencyService.checkInTransaction(reviewerId, COMMAND_REVIEW, requestKey, requestHash);

        if (appeal.getStatus() != AppealStatus.PENDING) {
            boolean sameDecision = (approve && appeal.getStatus() == AppealStatus.APPROVED)
                    || (!approve && appeal.getStatus() == AppealStatus.REJECTED);
            if (sameDecision) {
                // Repeat of the same decision: report the existing outcome.
                idempotencyService.recordSuccess(reviewerId, COMMAND_REVIEW, requestKey, requestHash,
                        "APPEAL", String.valueOf(appeal.getId()));
                return toDetail(appeal);
            }
            throw BusinessException.invalidState("该申诉已审核完成，不能重复审核");
        }
        if (!appeal.getVersion().equals(expectedVersion)) {
            throw BusinessException.invalidState("申诉版本已变更，请刷新后重试");
        }

        Violation violation = violationMapper.selectById(appeal.getViolationId());
        if (violation == null) {
            throw BusinessException.notFound("关联违约记录不存在");
        }
        if (approve) {
            if (violation.getStatus() != ViolationStatus.ACTIVE) {
                throw BusinessException.invalidState("该处罚已被撤销，不能审核通过");
            }
            int revoked = violationMapper.updateRevoked(violation.getId(), ViolationStatus.ACTIVE,
                    now, reviewerId);
            if (revoked != 1) {
                throw BusinessException.invalidState("处罚状态已变更，请刷新后重试");
            }
            refundDeduction(violation, appeal.getUserId(), reviewerId);
        }

        int updated = appealMapper.updateReview(appeal.getId(), expectedVersion,
                approve ? AppealStatus.APPROVED : AppealStatus.REJECTED, reviewerId,
                normalizedComment.isEmpty() ? null : normalizedComment, now);
        if (updated != 1) {
            throw BusinessException.invalidState("申诉状态已变更，请刷新后重试");
        }

        String eventKey = "appeal:" + appeal.getId() + (approve ? ":approved" : ":rejected");
        String title = approve ? "申诉审核通过" : "申诉审核未通过";
        String content = approve ? "您的违约申诉已通过，扣分已退回信用分。"
                : "您的违约申诉未通过：" + normalizedComment;
        notificationService.publish(appeal.getUserId(), eventKey, "APPEAL", title, content,
                violation.getReservationId(), violation.getId(), now);
        auditService.record(reviewerId, "APPEAL_REVIEW", "APPEAL", String.valueOf(appeal.getId()),
                "{\"decision\":\"" + normalizedDecision + "\",\"violationId\":" + violation.getId() + "}");
        idempotencyService.recordSuccess(reviewerId, COMMAND_REVIEW, requestKey, requestHash,
                "APPEAL", String.valueOf(appeal.getId()));
        return toDetail(appealMapper.selectById(appeal.getId()));
    }

    @Override
    public PageResult<AppealItem> listMine(Long userId, AppealStatus status, PageQuery pageQuery) {
        List<Appeal> appeals = appealMapper.selectByUserIdPage(userId, status,
                pageQuery.offset(), pageQuery.size());
        long total = appealMapper.countByUserId(userId, status);
        List<AppealItem> items = new ArrayList<>(appeals.size());
        for (Appeal appeal : appeals) {
            items.add(toItem(appeal));
        }
        return PageResult.of(items, total, pageQuery.page(), pageQuery.size());
    }

    @Override
    public AppealDetail getDetail(Long appealId, Long userId, boolean isAdmin) {
        Appeal appeal = appealMapper.selectById(appealId);
        if (appeal == null || (!isAdmin && !appeal.getUserId().equals(userId))) {
            throw BusinessException.notFound("申诉不存在");
        }
        return toDetail(appeal);
    }

    @Override
    public PageResult<AppealItem> adminList(AppealStatus status, String account, LocalDate dateFrom,
                                            LocalDate dateTo, PageQuery pageQuery) {
        if (dateFrom != null && dateTo != null) {
            long days = ChronoUnit.DAYS.between(dateFrom, dateTo);
            if (days < 0) {
                throw BusinessException.validation("dateFrom 不能晚于 dateTo");
            }
            if (days > ADMIN_RANGE_MAX_DAYS) {
                throw BusinessException.validation("管理员查询区间上限为 31 天");
            }
        }
        String normalizedAccount = account == null || account.isBlank()
                ? null : account.trim().toUpperCase(Locale.ROOT);
        List<Appeal> appeals = appealMapper.selectAdminPage(status, normalizedAccount, dateFrom, dateTo,
                pageQuery.offset(), pageQuery.size());
        long total = appealMapper.countAdminPage(status, normalizedAccount, dateFrom, dateTo);
        List<AppealItem> items = new ArrayList<>(appeals.size());
        for (Appeal appeal : appeals) {
            items.add(toItem(appeal));
        }
        return PageResult.of(items, total, pageQuery.page(), pageQuery.size());
    }

    /** Refund of the original deduction; the credit service clamps at the cap. */
    private void refundDeduction(Violation violation, Long userId, Long reviewerId) {
        int requestedDelta = 0;
        if (violation.getDeductionLogId() != null) {
            CreditLog deduction = creditLogMapper.selectById(violation.getDeductionLogId());
            if (deduction != null && deduction.getActualDelta() != null) {
                requestedDelta = -deduction.getActualDelta();
            }
        }
        CreditCommand command = new CreditCommand(
                "violation:" + violation.getId() + ":refund",
                userId,
                requestedDelta,
                CreditLogType.APPEAL_REFUND,
                violation.getReservationId(),
                violation.getId(),
                violation.getDeductionLogId(),
                "申诉通过退回扣分",
                reviewerId);
        creditService.apply(command);
    }

    private AppealItem toItem(Appeal appeal) {
        Violation violation = violationMapper.selectById(appeal.getViolationId());
        return new AppealItem(
                appeal.getId(),
                appeal.getViolationId(),
                violation == null ? null : violation.getReason().name(),
                violation == null ? null : clock.toOffset(violation.getEventAt()),
                violation == null ? null : deductedOf(violation),
                appeal.getStatus(),
                appeal.getReviewComment(),
                clock.toOffset(appeal.getSubmittedAt()),
                clock.toOffset(appeal.getReviewedAt()));
    }

    private AppealDetail toDetail(Appeal appeal) {
        Violation violation = violationMapper.selectById(appeal.getViolationId());
        List<Long> fileIds = new ArrayList<>();
        for (AppealFile appealFile : appealFileMapper.selectByAppealId(appeal.getId())) {
            fileIds.add(appealFile.getFileId());
        }
        return new AppealDetail(
                appeal.getId(),
                appeal.getViolationId(),
                violation == null ? null : violation.getReservationId(),
                violation == null ? null : violation.getReason().name(),
                violation == null ? null : clock.toOffset(violation.getEventAt()),
                violation == null ? null : deductedOf(violation),
                appeal.getReason(),
                fileIds,
                appeal.getStatus(),
                appeal.getReviewComment(),
                clock.toOffset(appeal.getSubmittedAt()),
                clock.toOffset(appeal.getReviewedAt()),
                appeal.getReviewerId());
    }

    private Integer deductedOf(Violation violation) {
        if (violation.getDeductionLogId() == null) {
            return null;
        }
        CreditLog deduction = creditLogMapper.selectById(violation.getDeductionLogId());
        if (deduction == null || deduction.getActualDelta() == null) {
            return null;
        }
        return -deduction.getActualDelta();
    }
}
