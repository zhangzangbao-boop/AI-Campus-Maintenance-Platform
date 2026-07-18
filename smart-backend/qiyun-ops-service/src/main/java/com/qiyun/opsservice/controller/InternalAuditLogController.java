package com.qiyun.opsservice.controller;

import com.qiyun.feign.client.OpsServiceClient;
import com.qiyun.opsservice.service.AuditLogService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal/audit-logs")
public class InternalAuditLogController {

    private final AuditLogService auditLogService;
    private final String internalSecret;

    public InternalAuditLogController(
            AuditLogService auditLogService,
            @Value("${internal.service.secret:}") String internalSecret) {
        this.auditLogService = auditLogService;
        this.internalSecret = (internalSecret != null && !internalSecret.isBlank())
            ? internalSecret
            : System.getenv("INTERNAL_SERVICE_SECRET");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> recordAuditLog(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody OpsServiceClient.AuditEventRequest request) {
        if (internalSecret == null || internalSecret.isBlank()) {
            log.error("INTERNAL_SERVICE_SECRET 未配置，拒绝内部审计写入请求");
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "服务配置错误：内部密钥未配置"
            ));
        }
        if (!internalSecret.equals(secret)) {
            log.warn("内部审计写入接口认证失败");
            return ResponseEntity.status(401).body(Map.of(
                "code", 401,
                "message", "认证失败"
            ));
        }
        if (request == null || isBlank(request.module()) || isBlank(request.action())) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", "审计模块和动作不能为空"
            ));
        }

        auditLogService.recordExternal(
            blankToNull(request.actorId()),
            request.module(),
            request.action(),
            blankToNull(request.targetType()),
            blankToNull(request.targetId()),
            request.detail(),
            request.success() == null || request.success()
        );
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "message", "审计记录已保存"
        ));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }
}
