package com.qiyun.repairservice.service;

import com.qiyun.feign.client.OpsServiceClient;
import com.qiyun.repairservice.domain.entity.Notification;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.repository.NotificationRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知推送集成服务
 * 在保存通知后，通过 Feign 调用 ops-service 进行实时推送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushService {

    private final NotificationRepository notificationRepository;
    private final OpsServiceClient opsServiceClient;

    // 内部服务密钥
    private static final String INTERNAL_SECRET = System.getenv().getOrDefault(
        "INTERNAL_SERVICE_SECRET",
        "change-this-secret-in-production"
    );

    /**
     * 发送通知并实时推送
     */
    @Transactional
    public void notifyAndPush(UserReference receiver, String title, String content, RepairTicket ticket) {
        if (receiver == null || title == null || title.isBlank() || content == null || content.isBlank()) {
            return;
        }

        // 1. 保存通知到数据库
        Notification notification = new Notification();
        notification.setReceiver(receiver);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedOrderId(ticket != null ? ticket.getTicketId() : null);
        notification.setReadFlag(false);
        notification.setCreatedAt(java.time.LocalDateTime.now());
        notificationRepository.save(notification);

        // 2. 异步推送实时通知（失败不影响主流程）
        pushRealTime(receiver.getUserId(), title, content, ticket != null ? ticket.getTicketId() : null);
    }

    /**
     * 批量发送通知并实时推送
     */
    @Transactional
    public void notifyAndPushBatch(List<UserReference> receivers, String title, String content, RepairTicket ticket) {
        if (receivers == null || receivers.isEmpty()) {
            return;
        }

        Long ticketId = ticket != null ? ticket.getTicketId() : null;

        // 1. 批量保存通知
        for (UserReference receiver : receivers) {
            Notification notification = new Notification();
            notification.setReceiver(receiver);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setRelatedOrderId(ticketId);
            notification.setReadFlag(false);
            notification.setCreatedAt(java.time.LocalDateTime.now());
            notificationRepository.save(notification);
        }

        // 2. 批量推送实时通知
        List<String> userIds = receivers.stream()
            .map(UserReference::getUserId)
            .collect(Collectors.toList());

        pushRealTimeBatch(userIds, title, content, ticketId);
    }

    /**
     * 推送实时通知给单个用户
     */
    private void pushRealTime(String userId, String title, String content, Long ticketId) {
        try {
            OpsServiceClient.NotificationPushRequest request =
                new OpsServiceClient.NotificationPushRequest(userId, title, content, ticketId);
            opsServiceClient.pushNotification(INTERNAL_SECRET, request);
            log.debug("实时通知推送成功: userId={}", userId);
        } catch (Exception e) {
            log.warn("实时通知推送失败（不影响主流程）: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 批量推送实时通知
     */
    private void pushRealTimeBatch(List<String> userIds, String title, String content, Long ticketId) {
        try {
            OpsServiceClient.NotificationBatchPushRequest request =
                new OpsServiceClient.NotificationBatchPushRequest(userIds, title, content, ticketId);
            opsServiceClient.pushNotificationBatch(INTERNAL_SECRET, request);
            log.debug("批量实时通知推送成功: count={}", userIds.size());
        } catch (Exception e) {
            log.warn("批量实时通知推送失败（不影响主流程）: error={}", e.getMessage());
        }
    }
}