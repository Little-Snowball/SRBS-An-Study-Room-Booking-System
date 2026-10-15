package edu.campus.studyroom.notification.service;

import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.PageQuery;
import edu.campus.studyroom.common.PageResult;
import edu.campus.studyroom.notification.NotificationService;
import edu.campus.studyroom.notification.domain.Notification;
import edu.campus.studyroom.notification.dto.NotificationItem;
import edu.campus.studyroom.notification.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内通知（手册 7.2.10 / 12.5）：本期即数据库记录，不引入 WebSocket。
 * 处罚/资源取消通知与业务变更同事务写入，因此 publish 为 MANDATORY——
 * 必须在调用方已开启的事务内调用（结算、申诉审核等核心写事务）。
 * <p>查询与已读标记（listForUser / markReadForUser）是控制器面的只读/轻写辅助方法，
 * 不属于跨模块冻结接口，仅供本模块控制器使用。
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final BusinessClock clock;

    public NotificationServiceImpl(NotificationMapper notificationMapper, BusinessClock clock) {
        this.notificationMapper = notificationMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(Long userId, String eventKey, String type, String title, String content,
                        Long reservationId, Long violationId, LocalDateTime publishedAt) {
        if (userId == null || eventKey == null || eventKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "通知参数不完整");
        }
        // 按 event_key 查重（UQ 保证唯一），重复则跳过，保证同一业务事件只通知一次
        if (notificationMapper.selectByEventKey(eventKey) != null) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setEventKey(eventKey);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setReservationId(reservationId);
        notification.setViolationId(violationId);
        notification.setPublishedAt(publishedAt);
        notificationMapper.insert(notification);
    }

    @Override
    public long unreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    /** 我的通知分页：unread=true 仅未读，按发布时间倒序。 */
    public PageResult<NotificationItem> listForUser(Long userId, Boolean unread, Integer page, Integer size) {
        PageQuery query = new PageQuery(page, size, null);
        List<Notification> rows =
                notificationMapper.selectByUserIdPage(userId, unread, query.offset(), query.size());
        long total = notificationMapper.countByUserId(userId, unread);
        List<NotificationItem> items = rows.stream().map(this::toItem).toList();
        return PageResult.of(items, total, query.page(), query.size());
    }

    /**
     * 标记已读（归属校验）。重复已读成功且无副作用；非本人或不存在返回 404。
     */
    public NotificationItem markReadForUser(Long notificationId, Long userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        if (notification.getReadAt() == null) {
            LocalDateTime readAt = clock.nowLocal();
            // 条件更新仅影响未读行；并发重复标记时 affectedRows 可能为 0，无副作用
            notificationMapper.markRead(notificationId, userId, readAt);
            notification.setReadAt(readAt);
        }
        return toItem(notification);
    }

    private NotificationItem toItem(Notification notification) {
        return new NotificationItem(
                String.valueOf(notification.getId()),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getReservationId() == null ? null : String.valueOf(notification.getReservationId()),
                notification.getViolationId() == null ? null : String.valueOf(notification.getViolationId()),
                notification.getReadAt() != null,
                clock.toOffset(notification.getPublishedAt()));
    }
}
