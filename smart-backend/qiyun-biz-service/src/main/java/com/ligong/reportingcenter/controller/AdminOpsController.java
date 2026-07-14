package com.ligong.reportingcenter.controller;

import com.ligong.reportingcenter.dto.AuditLogDto;
import com.ligong.reportingcenter.dto.SystemConfigDto;
import com.ligong.reportingcenter.dto.request.SystemConfigRequest;
import com.ligong.reportingcenter.service.AuditLogService;
import com.ligong.reportingcenter.service.FacilityHealthService;
import com.ligong.reportingcenter.service.SystemConfigService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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

/**
 * 管理员运维接口（审计日志、系统配置、设施健康）
 * 注意：转派审批已迁移至 repair-service AdminTransferController
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminOpsController {

    private final AuditLogService auditLogService;
    private final SystemConfigService systemConfigService;
    private final FacilityHealthService facilityHealthService;

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

    @GetMapping("/stats/facility-health")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> facilityHealth() {
        return success(facilityHealthService.getFacilityHealthIndex(), "获取成功");
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    private Map<String, Object> success(Object data, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }
}
