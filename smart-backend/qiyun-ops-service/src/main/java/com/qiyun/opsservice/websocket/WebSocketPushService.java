package com.qiyun.opsservice.websocket;

import com.qiyun.opsservice.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * WebSocket 推送服务
 * 负责向特定用户推送实时通知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 向指定用户推送通知
     *
     * @param userId       用户ID
     * @param notification 通知内容
     */
    public void pushNotification(String userId, NotificationDto notification) {
        if (userId == null || userId.isBlank()) {
            log.warn("推送通知失败：用户ID为空");
            return;
        }

        try {
            // 推送到用户专属频道 /topic/user/{userId}
            String destination = "/topic/user/" + userId;
            messagingTemplate.convertAndSend(destination, notification);
            log.info("实时通知推送成功: userId={}, destination={}", userId, destination);
        } catch (Exception e) {
            log.error("实时通知推送失败: userId={}, error={}", userId, e.getMessage(), e);
        }
    }

    /**
     * 批量推送通知给多个用户
     *
     * @param userIds      用户ID列表
     * @param notification 通知内容
     */
    public void pushNotificationToMany(Iterable<String> userIds, NotificationDto notification) {
        for (String userId : userIds) {
            pushNotification(userId, notification);
        }
    }
}