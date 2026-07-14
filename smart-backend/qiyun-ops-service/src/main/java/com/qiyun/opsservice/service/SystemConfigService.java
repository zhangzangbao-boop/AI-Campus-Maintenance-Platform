package com.qiyun.opsservice.service;

import com.qiyun.opsservice.domain.entity.SystemConfig;
import com.qiyun.opsservice.domain.entity.UserReference;
import com.qiyun.opsservice.dto.SystemConfigDto;
import com.qiyun.opsservice.dto.request.SystemConfigRequest;
import com.qiyun.opsservice.repository.SystemConfigRepository;
import com.qiyun.opsservice.repository.UserReferenceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final UserReferenceRepository userReferenceRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<SystemConfigDto> list() {
        Map<String, SystemConfigDto> merged = new LinkedHashMap<>();
        defaultConfigs().forEach((key, item) -> merged.put(key, item));
        systemConfigRepository.findAll().stream()
            .map(this::toDto)
            .filter(item -> !isDeprecatedConfig(item.configKey()))
            .forEach(item -> merged.put(item.configKey(), item));
        return List.copyOf(merged.values());
    }

    @Transactional
    public SystemConfigDto save(String key, SystemConfigRequest request, String operatorId) {
        SystemConfig config = systemConfigRepository.findById(key).orElseGet(() -> {
            SystemConfig item = new SystemConfig();
            item.setConfigKey(key);
            return item;
        });
        config.setConfigValue(request.configValue());
        config.setDescription(request.description());
        if (operatorId != null && !operatorId.isBlank()) {
            try {
                UserReference operator = userReferenceRepository.findByUserIdAndIsActiveTrue(operatorId).orElse(null);
                if (operator != null) {
                    config.setUpdatedBy(operator);
                }
            } catch (Exception ignored) {
            }
        }
        systemConfigRepository.save(config);
        auditLogService.record("系统配置", "更新配置", "SYSTEM_CONFIG", key, "更新系统配置：" + key);
        return toDto(config);
    }

    @Transactional(readOnly = true)
    public String getValue(String key, String fallback) {
        return systemConfigRepository.findById(key)
            .map(SystemConfig::getConfigValue)
            .filter(value -> !value.isBlank())
            .orElse(fallback);
    }

    private SystemConfigDto toDto(SystemConfig config) {
        return new SystemConfigDto(
            config.getConfigKey(),
            config.getConfigValue(),
            config.getDescription(),
            config.getUpdatedBy() != null ? config.getUpdatedBy().getUserId() : null,
            config.getCreatedAt(),
            config.getUpdatedAt()
        );
    }

    private Map<String, SystemConfigDto> defaultConfigs() {
        Map<String, SystemConfigDto> defaults = new LinkedHashMap<>();
        putDefault(defaults, "ai.enabled", "false", "是否启用外部大模型。false 时使用本地规则引擎。");
        putDefault(defaults, "ai.provider", "deepseek", "大模型供应商，当前支持 DeepSeek/OpenAI-Compatible 接口。");
        putDefault(defaults, "ai.base-url", "https://api.deepseek.com", "DeepSeek OpenAI-Compatible API Base URL。");
        putDefault(defaults, "ai.api-key", "", "DeepSeek API Key。建议只在本地后台配置，不写入公开 SQL。");
        putDefault(defaults, "ai.model", "deepseek-v4-flash", "默认大模型名称，可按 DeepSeek 控制台可用模型调整。");
        putDefault(defaults, "ai.timeout-seconds", "20", "大模型接口超时时间，单位秒。");
        putDefault(defaults, "ai.thinking.enabled", "false", "是否启用支持思考模式模型的 thinking 参数。");
        putDefault(defaults, "upload.max-image-count", "5", "单个工单最多上传图片数量。");
        putDefault(defaults, "upload.max-image-size-mb", "5", "单张图片大小上限，单位 MB。");
        putDefault(defaults, "backup.auto-enabled", "false", "是否启用定时数据库备份。");
        putDefault(defaults, "backup.cron", "0 30 2 * * ?", "定时备份 Cron 表达式，默认每天凌晨 2:30。");
        putDefault(defaults, "backup.retention-days", "30", "备份文件保留天数。");
        putDefault(defaults, "sla.high.responseHours", "2", "高优先级受理时限，单位小时。");
        putDefault(defaults, "sla.high.completionHours", "24", "高优先级完成时限，单位小时。");
        putDefault(defaults, "sla.medium.responseHours", "8", "中优先级受理时限，单位小时。");
        putDefault(defaults, "sla.medium.completionHours", "72", "中优先级完成时限，单位小时。");
        putDefault(defaults, "sla.low.responseHours", "24", "低优先级受理时限，单位小时。");
        putDefault(defaults, "sla.low.completionHours", "168", "低优先级完成时限，单位小时。");
        return defaults;
    }

    private void putDefault(Map<String, SystemConfigDto> defaults, String key, String value, String description) {
        defaults.put(key, new SystemConfigDto(key, value, description, null, null, null));
    }

    private boolean isDeprecatedConfig(String key) {
        return key != null && key.startsWith("vector.");
    }
}