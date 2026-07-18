package com.qiyun.opsservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.common.exception.BusinessException;
import com.qiyun.opsservice.domain.entity.SystemConfig;
import com.qiyun.opsservice.domain.entity.UserReference;
import com.qiyun.opsservice.dto.SystemConfigDto;
import com.qiyun.opsservice.dto.request.SystemConfigRequest;
import com.qiyun.opsservice.repository.SystemConfigRepository;
import com.qiyun.opsservice.repository.UserReferenceRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final UserReferenceRepository userReferenceRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public static final String AI_CATEGORY_KEYWORDS = "ai.ticket.category-keywords";
    public static final String AI_URGENCY_RULES = "ai.ticket.urgency-rules";
    public static final String SLA_RULES = "sla.ticket.rules";
    public static final String FAULT_TREND_RULES = "fault-trend.rules";

    private static final String DEFAULT_AI_CATEGORY_KEYWORDS = """
        [
          {"category":"空调故障","keywords":["空调","制冷","制热","风机","遥控器"]},
          {"category":"管道故障","keywords":["漏水","滴水","水管","水龙头","下水","地漏","积水","堵塞"]},
          {"category":"电力故障","keywords":["断电","跳闸","插座","电路","电线","开关","照明","灯","频闪"]},
          {"category":"网络故障","keywords":["网络","wifi","无线","网口","断线","校园网","无法连接"]},
          {"category":"家具故障","keywords":["桌","椅","床","柜","门锁","家具"]},
          {"category":"门窗故障","keywords":["门","窗","玻璃","闭门器"]}
        ]
        """;
    private static final String DEFAULT_AI_URGENCY_RULES = """
        [
          {"urgency":"紧急","keywords":["漏水","积水","触电","烧焦","冒烟","火花","异味","总闸","消防","灭火器","玻璃","危险"]},
          {"urgency":"普通","keywords":["无法使用","不能用","断线","频闪","损坏","不制冷","堵塞","松动","脱落"]}
        ]
        """;
    private static final String DEFAULT_SLA_RULES = """
        {
          "warningRatio":0.25,
          "priorities":{
            "high":{"responseHours":2,"completionHours":24},
            "medium":{"responseHours":8,"completionHours":72},
            "low":{"responseHours":24,"completionHours":168}
          }
        }
        """;
    private static final String DEFAULT_FAULT_TREND_RULES = """
        {
          "sevenDays":{"countThreshold":3,"growthThreshold":50.0},
          "thirtyDays":{"countThreshold":6,"growthThreshold":30.0}
        }
        """;

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
        validate(key, request.configValue());
        SystemConfig config = systemConfigRepository.findById(key).orElseGet(() -> {
            SystemConfig item = new SystemConfig();
            item.setConfigKey(key);
            return item;
        });
        String before = config.getConfigValue();
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
        auditLogService.record("系统配置", "更新配置", "SYSTEM_CONFIG", key,
            "更新系统配置：" + key
                + "；修改人=" + operatorId
                + "；修改时间=" + LocalDateTime.now()
                + "；修改前=" + safeAuditText(before)
                + "；修改后=" + safeAuditText(config.getConfigValue()));
        return toDto(config);
    }

    @Transactional(readOnly = true)
    public String getValue(String key, String fallback) {
        return systemConfigRepository.findById(key)
            .map(SystemConfig::getConfigValue)
            .filter(value -> !value.isBlank())
            .orElse(fallback);
    }

    @Transactional(readOnly = true)
    public Map<String, String> repairRuleConfig() {
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put(AI_CATEGORY_KEYWORDS, getValue(AI_CATEGORY_KEYWORDS, DEFAULT_AI_CATEGORY_KEYWORDS));
        rules.put(AI_URGENCY_RULES, getValue(AI_URGENCY_RULES, DEFAULT_AI_URGENCY_RULES));
        rules.put(SLA_RULES, getValue(SLA_RULES, DEFAULT_SLA_RULES));
        rules.put(FAULT_TREND_RULES, getValue(FAULT_TREND_RULES, DEFAULT_FAULT_TREND_RULES));
        return rules;
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
        putDefault(defaults, AI_CATEGORY_KEYWORDS, DEFAULT_AI_CATEGORY_KEYWORDS, "AI规则引擎：关键词与工单分类映射，结构化编辑。");
        putDefault(defaults, AI_URGENCY_RULES, DEFAULT_AI_URGENCY_RULES, "AI规则引擎：紧急程度判定关键词，结构化编辑。");
        putDefault(defaults, SLA_RULES, DEFAULT_SLA_RULES, "SLA受理/完成/预警阈值，结构化编辑。");
        putDefault(defaults, FAULT_TREND_RULES, DEFAULT_FAULT_TREND_RULES, "高频故障7天、30天触发阈值，结构化编辑。");
        return defaults;
    }

    private void putDefault(Map<String, SystemConfigDto> defaults, String key, String value, String description) {
        defaults.put(key, new SystemConfigDto(key, value, description, null, null, null));
    }

    private boolean isDeprecatedConfig(String key) {
        return key != null && key.startsWith("vector.");
    }

    private void validate(String key, String value) {
        if (value == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "配置值不能为空");
        }
        switch (key) {
            case AI_CATEGORY_KEYWORDS -> validateKeywordRules(value, "category", "分类");
            case AI_URGENCY_RULES -> validateKeywordRules(value, "urgency", "紧急程度");
            case SLA_RULES -> validateSlaRules(value);
            case FAULT_TREND_RULES -> validateFaultTrendRules(value);
            case "ai.timeout-seconds", "upload.max-image-count", "upload.max-image-size-mb",
                 "backup.retention-days", "sla.high.responseHours", "sla.high.completionHours",
                 "sla.medium.responseHours", "sla.medium.completionHours", "sla.low.responseHours",
                 "sla.low.completionHours" -> validateInteger(value, key, 1, 720);
            default -> {
            }
        }
    }

    private void validateKeywordRules(String value, String labelField, String labelName) {
        JsonNode root = parseJson(value);
        if (!root.isArray() || root.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, labelName + "规则必须是非空数组");
        }
        for (JsonNode item : root) {
            if (!item.hasNonNull(labelField) || item.get(labelField).asText().isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, labelName + "名称不能为空");
            }
            JsonNode keywords = item.get("keywords");
            if (keywords == null || !keywords.isArray() || keywords.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, labelName + "关键词必须是非空数组");
            }
            for (JsonNode keyword : keywords) {
                if (!keyword.isTextual() || keyword.asText().isBlank() || keyword.asText().length() > 30) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "关键词必须是1-30字符文本");
                }
            }
        }
    }

    private void validateSlaRules(String value) {
        JsonNode root = parseJson(value);
        double warningRatio = requiredNumber(root, "warningRatio", 0.01, 0.90);
        JsonNode priorities = root.get("priorities");
        if (warningRatio <= 0 || priorities == null || !priorities.isObject()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SLA规则结构不正确");
        }
        for (String priority : List.of("high", "medium", "low")) {
            JsonNode item = priorities.get(priority);
            if (item == null || !item.isObject()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "缺少" + priority + "优先级SLA规则");
            }
            requiredNumber(item, "responseHours", 1, 720);
            requiredNumber(item, "completionHours", 1, 2160);
            if (item.get("completionHours").asLong() < item.get("responseHours").asLong()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "完成时限不能小于受理时限");
            }
        }
    }

    private void validateFaultTrendRules(String value) {
        JsonNode root = parseJson(value);
        validateFaultPeriod(root, "sevenDays");
        validateFaultPeriod(root, "thirtyDays");
    }

    private void validateFaultPeriod(JsonNode root, String key) {
        JsonNode item = root.get(key);
        if (item == null || !item.isObject()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "缺少" + key + "高频故障规则");
        }
        requiredNumber(item, "countThreshold", 1, 1000);
        requiredNumber(item, "growthThreshold", 0, 10000);
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "配置JSON格式不正确");
        }
    }

    private double requiredNumber(JsonNode node, String field, double min, double max) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, field + "必须是数字");
        }
        double number = value.asDouble();
        if (number < min || number > max) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, field + "超出允许范围");
        }
        return number;
    }

    private void validateInteger(String value, String key, int min, int max) {
        try {
            int number = Integer.parseInt(value.trim());
            if (number < min || number > max) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, key + "超出允许范围");
            }
        } catch (NumberFormatException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, key + "必须是整数");
        }
    }

    private String safeAuditText(String value) {
        if (value == null) {
            return "<未配置>";
        }
        return value.length() > 1500 ? value.substring(0, 1500) + "...(已截断)" : value;
    }
}
