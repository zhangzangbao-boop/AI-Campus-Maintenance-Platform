package com.qiyun.opsservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.opsservice.domain.entity.Notification;
import com.qiyun.opsservice.domain.entity.UserReference;
import com.qiyun.opsservice.dto.NotificationDto;
import com.qiyun.opsservice.repository.NotificationRepository;
import com.qiyun.opsservice.repository.UserReferenceRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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