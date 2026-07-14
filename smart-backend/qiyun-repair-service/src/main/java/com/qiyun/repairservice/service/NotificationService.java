package com.qiyun.repairservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.repairservice.domain.entity.Notification;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.dto.NotificationDto;
import com.qiyun.repairservice.repository.NotificationRepository;
import com.qiyun.repairservice.repository.UserReferenceRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知服务（维修服务专用）
 * 提供工单相关的通知功能
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserReferenceRepository userReferenceRepository;

    @Transactional(readOnly = true)
    public List<NotificationDto> listMine(String userId) {
        UserReference receiver = loadActiveUser(userId);
        return notificationRepository.findByReceiverOrderByCreatedAtDesc(receiver)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(String userId) {
        UserReference receiver = loadActiveUser(userId);
        return notificationRepository.countByReceiverAndReadFlagFalse(receiver);
    }

    @Transactional
    public NotificationDto markRead(Long notificationId, String userId) {
        UserReference receiver = loadActiveUser(userId);
        Notification notification = notificationRepository.findByNotificationIdAndReceiver(notificationId, receiver)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "通知不存在"));
        notification.setReadFlag(true);
        return toDto(notification);
    }

    @Transactional
    public long markAllRead(String userId) {
        UserReference receiver = loadActiveUser(userId);
        List<Notification> notifications = notificationRepository.findByReceiverOrderByCreatedAtDesc(receiver);
        long changed = 0;
        for (Notification notification : notifications) {
            if (!Boolean.TRUE.equals(notification.getReadFlag())) {
                notification.setReadFlag(true);
                changed++;
            }
        }
        return changed;
    }

    /**
     * 发送通知给指定用户
     */
    @Transactional
    public void notifyUser(UserReference receiver, String title, String content, RepairTicket ticket) {
        if (receiver == null || title == null || title.isBlank() || content == null || content.isBlank()) {
            return;
        }
        Notification notification = new Notification();
        notification.setReceiver(receiver);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedOrderId(ticket != null ? ticket.getTicketId() : null);
        notification.setReadFlag(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    /**
     * 发送通知给所有管理员
     */
    @Transactional
    public void notifyAdmins(String title, String content, RepairTicket ticket) {
        List<UserReference> admins = userReferenceRepository.findByRoleAndIsActiveTrue(UserRole.ADMIN);
        for (UserReference admin : admins) {
            notifyUser(admin, title, content, ticket);
        }
    }

    /**
     * 加载活跃用户
     */
    private UserReference loadActiveUser(String userId) {
        return userReferenceRepository.findByUserIdAndIsActiveTrue(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在或已禁用"));
    }

    private NotificationDto toDto(Notification notification) {
        return new NotificationDto(
            notification.getNotificationId(),
            notification.getReceiver() != null ? notification.getReceiver().getUserId() : null,
            notification.getTitle(),
            notification.getContent(),
            notification.getRelatedOrderId(),
            Boolean.TRUE.equals(notification.getReadFlag()),
            notification.getCreatedAt()
        );
    }
}