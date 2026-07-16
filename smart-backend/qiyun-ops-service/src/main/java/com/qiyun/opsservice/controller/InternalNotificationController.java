package com.qiyun.opsservice.controller;

import com.qiyun.opsservice.dto.NotificationDto;
import com.qiyun.opsservice.websocket.WebSocketPushService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部通知推送接口
 * 仅供服务间调用，需要内部密钥认证
 */
@Slf4j
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final WebSocketPushService webSocketPushService;

    // 内部服务密钥，从环境变量读取
    private static final String INTERNAL_SECRET = System.getenv().getOrDefault(
        "INTERNAL_SERVICE_SECRET",
        "change-this-secret-in-production"
    );

    /**
     * 推送通知给单个用户
     * POST /internal/notifications/push
     */
    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> pushNotification(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody NotificationPushRequest request) {

        // 验证内部密钥
        if (!INTERNAL_SECRET.equals(secret)) {
            log.warn("内部推送接口认证失败");
            return ResponseEntity.status(401).body(Map.of(
                "code", 401,
                "message", "认证失败"
            ));
        }

        if (request.userId() == null || request.userId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", "用户ID不能为空"
            ));
        }

        NotificationDto notification = new NotificationDto(
            null,
            request.userId(),
            request.title(),
            request.content(),
            request.relatedOrderId(),
            false,
            null
        );

        webSocketPushService.pushNotification(request.userId(), notification);

        return ResponseEntity.ok(Map.of(
            "code", 200,
            "message", "推送成功"
        ));
    }

    /**
     * 批量推送通知给多个用户
     * POST /internal/notifications/push-batch
     */
    @PostMapping("/push-batch")
    public ResponseEntity<Map<String, Object>> pushNotificationBatch(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody NotificationBatchPushRequest request) {

        // 验证内部密钥
        if (!INTERNAL_SECRET.equals(secret)) {
            log.warn("内部批量推送接口认证失败");
            return ResponseEntity.status(401).body(Map.of(
                "code", 401,
                "message", "认证失败"
            ));
        }

        if (request.userIds() == null || request.userIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", "用户ID列表不能为空"
            ));
        }

        NotificationDto notification = new NotificationDto(
            null,
            null,
            request.title(),
            request.content(),
            request.relatedOrderId(),
            false,
            null
        );

        webSocketPushService.pushNotificationToMany(request.userIds(), notification);

        return ResponseEntity.ok(Map.of(
            "code", 200,
            "message", "批量推送成功",
            "data", Map.of("pushedCount", request.userIds().size())
        ));
    }

    /**
     * 单用户推送请求
     */
    public record NotificationPushRequest(
        String userId,
        String title,
        String content,
        Long relatedOrderId
    ) {}

    /**
     * 批量推送请求
     */
    public record NotificationBatchPushRequest(
        List<String> userIds,
        String title,
        String content,
        Long relatedOrderId
    ) {}
}