package com.qiyun.repairservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.feign.client.OpsServiceClient;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepairRuleConfigService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final String SLA_RULES = "sla.ticket.rules";
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

    private final OpsServiceClient opsServiceClient;
    private final ObjectMapper objectMapper;

    @Value("${internal.service.secret:}")
    private String configuredInternalSecret;

    private volatile SlaRules cachedSlaRules;
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public SlaRules slaRules() {
        Instant now = Instant.now();
        SlaRules cached = cachedSlaRules;
        if (cached != null && now.isBefore(cacheExpiresAt)) {
            return cached;
        }
        SlaRules loaded = loadSlaRules();
        cachedSlaRules = loaded;
        cacheExpiresAt = now.plus(CACHE_TTL);
        return loaded;
    }

    public void clearCacheForTest() {
        cachedSlaRules = null;
        cacheExpiresAt = Instant.EPOCH;
    }

    private SlaRules loadSlaRules() {
        String secret = internalSecret();
        if (secret.isBlank()) {
            log.warn("INTERNAL_SERVICE_SECRET 未配置，SLA规则使用默认值");
            return parseSlaRules(DEFAULT_SLA_RULES);
        }
        try {
            Map<String, Object> response = opsServiceClient.getRepairRuleConfig(secret);
            Object data = response == null ? null : response.get("data");
            if (data instanceof Map<?, ?> map && map.get(SLA_RULES) != null) {
                return parseSlaRules(String.valueOf(map.get(SLA_RULES)));
            }
        } catch (Exception e) {
            log.warn("读取SLA配置失败，使用默认规则: {}", e.getMessage());
        }
        return parseSlaRules(DEFAULT_SLA_RULES);
    }

    private SlaRules parseSlaRules(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            double warningRatio = root.path("warningRatio").asDouble(0.25);
            Map<String, SlaPriorityRule> priorities = new LinkedHashMap<>();
            JsonNode priorityNode = root.path("priorities");
            priorities.put("high", parsePriority(priorityNode.path("high"), 2, 24));
            priorities.put("medium", parsePriority(priorityNode.path("medium"), 8, 72));
            priorities.put("low", parsePriority(priorityNode.path("low"), 24, 168));
            return new SlaRules(Math.max(0.01, Math.min(0.90, warningRatio)), priorities);
        } catch (Exception e) {
            log.warn("解析SLA配置失败，使用内置默认值: {}", e.getMessage());
            return new SlaRules(0.25, Map.of(
                "high", new SlaPriorityRule(2, 24),
                "medium", new SlaPriorityRule(8, 72),
                "low", new SlaPriorityRule(24, 168)
            ));
        }
    }

    private SlaPriorityRule parsePriority(JsonNode node, long defaultResponse, long defaultCompletion) {
        long responseHours = node.path("responseHours").asLong(defaultResponse);
        long completionHours = node.path("completionHours").asLong(defaultCompletion);
        if (responseHours < 1 || completionHours < responseHours) {
            return new SlaPriorityRule(defaultResponse, defaultCompletion);
        }
        return new SlaPriorityRule(responseHours, completionHours);
    }

    private String internalSecret() {
        if (configuredInternalSecret != null && !configuredInternalSecret.isBlank()) {
            return configuredInternalSecret;
        }
        String envSecret = System.getenv("INTERNAL_SERVICE_SECRET");
        return envSecret == null ? "" : envSecret;
    }

    public record SlaRules(double warningRatio, Map<String, SlaPriorityRule> priorities) {
        public SlaPriorityRule priority(String priority) {
            return priorities.getOrDefault(priority, priorities.getOrDefault("medium", new SlaPriorityRule(8, 72)));
        }
    }

    public record SlaPriorityRule(long responseHours, long completionHours) {}
}
