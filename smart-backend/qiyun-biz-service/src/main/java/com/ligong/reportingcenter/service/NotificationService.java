package com.ligong.reportingcenter.service;

import com.ligong.reportingcenter.domain.entity.Notification;
import com.ligong.reportingcenter.domain.entity.RepairTicket;
import com.ligong.reportingcenter.domain.entity.User;
import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.NotificationDto;
import com.qiyun.common.exception.BusinessException;
import com.ligong.reportingcenter.repository.NotificationRepository;
import com.ligong.reportingcenter.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<NotificationDto> listMine(String userId) {
        User receiver = userService.loadActiveUser(userId);
        return notificationRepository.findByReceiverOrderByCreatedAtDesc(receiver)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(String userId) {
        User receiver = userService.loadActiveUser(userId);
        return notificationRepository.countByReceiverAndReadFlagFalse(receiver);
    }

    @Transactional
    public NotificationDto markRead(Long notificationId, String userId) {
        User receiver = userService.loadActiveUser(userId);
        Notification notification = notificationRepository.findByNotificationIdAndReceiver(notificationId, receiver)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "通知不存在"));
        notification.setReadFlag(true);
        return toDto(notification);
    }

    @Transactional
    public long markAllRead(String userId) {
        User receiver = userService.loadActiveUser(userId);
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

    @Transactional
    public void notifyUser(User receiver, String title, String content, RepairTicket ticket) {
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

    @Transactional
    public void notifyAdmins(String title, String content, RepairTicket ticket) {
        List<User> admins = userRepository.findByRoleAndIsActiveTrue(UserRole.ADMIN);
        for (User admin : admins) {
            notifyUser(admin, title, content, ticket);
        }
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
