package com.qiyun.repairservice.service;

import com.qiyun.feign.client.OpsServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEventPublisher {

    private final OpsServiceClient opsServiceClient;

    @Value("${internal.service.secret:}")
    private String internalSecret;

    public void record(String actorId, String module, String action, String targetType, String targetId,
                       String detail) {
        record(actorId, module, action, targetType, targetId, detail, true);
    }

    public void record(String actorId, String module, String action, String targetType, String targetId,
                       String detail, boolean success) {
        String secret = hasText(internalSecret) ? internalSecret : System.getenv("INTERNAL_SERVICE_SECRET");
        if (!hasText(secret)) {
            log.warn("INTERNAL_SERVICE_SECRET 未配置，跳过管理员审计上报: module={}, action={}, target={}:{}",
                module, action, targetType, targetId);
            return;
        }
        try {
            opsServiceClient.recordAuditLog(secret, new OpsServiceClient.AuditEventRequest(
                actorId, module, action, targetType, targetId, detail, success
            ));
        } catch (Exception e) {
            log.warn("管理员审计上报失败，不影响主流程: module={}, action={}, target={}:{}, error={}",
                module, action, targetType, targetId, e.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
