package com.qiyun.feign.client;

import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Ops 服务 Feign 客户端
 * 用于服务间调用 ops-service 的内部接口
 */
@FeignClient(name = "qiyun-ops-service", path = "/internal")
public interface OpsServiceClient {

    /**
     * 推送通知给单个用户
     */
    @PostMapping("/notifications/push")
    Map<String, Object> pushNotification(
        @RequestHeader("X-Internal-Secret") String secret,
        @RequestBody NotificationPushRequest request
    );

    /**
     * 批量推送通知给多个用户
     */
    @PostMapping("/notifications/push-batch")
    Map<String, Object> pushNotificationBatch(
        @RequestHeader("X-Internal-Secret") String secret,
        @RequestBody NotificationBatchPushRequest request
    );

    /**
     * 读取报修分析和预警规则配置。
     */
    @GetMapping("/system-config/repair-rules")
    Map<String, Object> getRepairRuleConfig(@RequestHeader("X-Internal-Secret") String secret);

    /**
     * 记录跨服务管理员审计事件。
     */
    @PostMapping("/audit-logs")
    Map<String, Object> recordAuditLog(
        @RequestHeader("X-Internal-Secret") String secret,
        @RequestBody AuditEventRequest request
    );

    /**
     * 单用户推送请求
     */
    record NotificationPushRequest(
        String userId,
        String title,
        String content,
        Long relatedOrderId
    ) {}

    /**
     * 批量推送请求
     */
    record NotificationBatchPushRequest(
        List<String> userIds,
        String title,
        String content,
        Long relatedOrderId
    ) {}

    /**
     * 跨服务审计请求。
     */
    record AuditEventRequest(
        String actorId,
        String module,
        String action,
        String targetType,
        String targetId,
        String detail,
        Boolean success
    ) {}
}
