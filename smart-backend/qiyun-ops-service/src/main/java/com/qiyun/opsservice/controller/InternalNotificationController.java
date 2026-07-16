package com.qiyun.opsservice.controller;

import com.qiyun.opsservice.dto.NotificationDto;
import com.qiyun.opsservice.websocket.WebSocketPushService;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class InternalNotificationController {

    private final WebSocketPushService webSocketPushService;
    private final String internalSecret;

    public InternalNotificationController(
            WebSocketPushService webSocketPushService,
            @Value("${internal.service.secret:}") String internalSecret) {
        this.webSocketPushService = webSocketPushService;
        // 优先使用配置，配置为空时尝试环境变量
        this.internalSecret = (internalSecret != null && !internalSecret.isBlank())
            ? internalSecret
            : System.getenv("INTERNAL_SERVICE_SECRET");
    }

    /**
     * 推送通知给单个用户
     * POST /internal/notifications/push
     */
    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> pushNotification(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody NotificationPushRequest request) {

        // 验证密钥配置
        if (internalSecret == null || internalSecret.isBlank()) {
            log.error("INTERNAL_SERVICE_SECRET 未配置，拒绝内部推送请求");
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "服务配置错误：内部密钥未配置"
            ));
        }

        // 验证内部密钥
        if (!internalSecret.equals(secret)) {
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

        // 验证密钥配置
        if (internalSecret == null || internalSecret.isBlank()) {
            log.error("INTERNAL_SERVICE_SECRET 未配置，拒绝内部批量推送请求");
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "服务配置错误：内部密钥未配置"
            ));
        }

        // 验证内部密钥
        if (!internalSecret.equals(secret)) {
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