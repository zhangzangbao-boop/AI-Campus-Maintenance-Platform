package com.qiyun.opsservice.controller;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.opsservice.dto.AuditLogDto;
import com.qiyun.opsservice.dto.SystemConfigDto;
import com.qiyun.opsservice.dto.request.SystemConfigRequest;
import com.qiyun.opsservice.service.AuditLogService;
import com.qiyun.opsservice.service.SystemConfigService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminOpsController {

    private final AuditLogService auditLogService;
    private final SystemConfigService systemConfigService;

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> listAuditLogs(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", defaultValue = "120") int limit) {
        List<AuditLogDto> logs = auditLogService.list(keyword, limit);
        return success(logs, "获取成功");
    }

    @GetMapping("/system-config")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> listSystemConfig() {
        List<SystemConfigDto> configs = systemConfigService.list();
        return success(configs, "获取成功");
    }

    @PutMapping("/system-config/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> saveSystemConfig(@PathVariable("key") String key,
                                                @RequestBody SystemConfigRequest request) {
        SystemConfigDto config = systemConfigService.save(key, request, currentUserId());
        return success(config, "保存成功");
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录后再访问");
        }
        return authentication.getName();
    }

    private Map<String, Object> success(Object data, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }
}