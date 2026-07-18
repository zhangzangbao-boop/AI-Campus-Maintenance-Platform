package com.qiyun.opsservice.controller;

import com.qiyun.opsservice.service.SystemConfigService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部系统配置接口，仅供服务间读取规则配置。
 */
@Slf4j
@RestController
@RequestMapping("/internal/system-config")
public class InternalSystemConfigController {

    private final SystemConfigService systemConfigService;
    private final String internalSecret;

    public InternalSystemConfigController(
            SystemConfigService systemConfigService,
            @Value("${internal.service.secret:}") String internalSecret) {
        this.systemConfigService = systemConfigService;
        this.internalSecret = (internalSecret != null && !internalSecret.isBlank())
            ? internalSecret
            : System.getenv("INTERNAL_SERVICE_SECRET");
    }

    @GetMapping("/repair-rules")
    public ResponseEntity<Map<String, Object>> getRepairRuleConfig(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (internalSecret == null || internalSecret.isBlank()) {
            log.error("INTERNAL_SERVICE_SECRET 未配置，拒绝内部配置读取请求");
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "服务配置错误：内部密钥未配置"
            ));
        }
        if (!internalSecret.equals(secret)) {
            log.warn("内部配置读取接口认证失败");
            return ResponseEntity.status(401).body(Map.of(
                "code", 401,
                "message", "认证失败"
            ));
        }
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "message", "获取成功",
            "data", systemConfigService.repairRuleConfig()
        ));
    }
}
