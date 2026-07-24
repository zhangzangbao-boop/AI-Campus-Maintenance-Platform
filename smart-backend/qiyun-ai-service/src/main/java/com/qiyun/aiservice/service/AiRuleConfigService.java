package com.qiyun.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRuleConfigService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final String AI_CATEGORY_KEYWORDS = "ai.ticket.category-keywords";
    private static final String AI_URGENCY_RULES = "ai.ticket.urgency-rules";
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
          {"urgency":"高","keywords":["触电","漏电","烧焦","冒烟","火花","火灾","被困","大范围停电","大范围停水","严重漏水"]},
          {"urgency":"中","keywords":["无法使用","不能用","断线","频闪","损坏","不制冷","堵塞","松动","脱落","漏水","滴水"]}
        ]
        """;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ops.service.base-url:http://localhost:9005}")
    private String opsServiceBaseUrl;

    @Value("${internal.service.secret:}")
    private String configuredInternalSecret;

    private volatile AiRules cachedRules;
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public AiRules rules() {
        Instant now = Instant.now();
        AiRules cached = cachedRules;
        if (cached != null && now.isBefore(cacheExpiresAt)) {
            return cached;
        }
        AiRules loaded = loadRules();
        cachedRules = loaded;
        cacheExpiresAt = now.plus(CACHE_TTL);
        return loaded;
    }

    public void clearCacheForTest() {
        cachedRules = null;
        cacheExpiresAt = Instant.EPOCH;
    }

    private AiRules loadRules() {
        String categoryJson = DEFAULT_AI_CATEGORY_KEYWORDS;
        String urgencyJson = DEFAULT_AI_URGENCY_RULES;
        String secret = internalSecret();
        if (!secret.isBlank()) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", secret);
                ResponseEntity<Map> response = restTemplate.exchange(
                    opsServiceBaseUrl.replaceAll("/$", "") + "/internal/system-config/repair-rules",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
                );
                Object data = response.getBody() == null ? null : response.getBody().get("data");
                if (data instanceof Map<?, ?> map) {
                    if (map.get(AI_CATEGORY_KEYWORDS) != null) {
                        categoryJson = String.valueOf(map.get(AI_CATEGORY_KEYWORDS));
                    }
                    if (map.get(AI_URGENCY_RULES) != null) {
                        urgencyJson = String.valueOf(map.get(AI_URGENCY_RULES));
                    }
                }
            } catch (Exception e) {
                log.warn("读取AI规则配置失败，使用默认规则: {}", e.getMessage());
            }
        } else {
            log.warn("INTERNAL_SERVICE_SECRET 未配置，AI规则使用默认值");
        }
        return new AiRules(parseKeywordRules(categoryJson, "category"), parseKeywordRules(urgencyJson, "urgency"));
    }

    private List<KeywordRule> parseKeywordRules(String json, String labelField) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<KeywordRule> rules = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode item : root) {
                    String label = item.path(labelField).asText("");
                    JsonNode keywordsNode = item.path("keywords");
                    List<String> keywords = new ArrayList<>();
                    if (keywordsNode.isArray()) {
                        for (JsonNode keyword : keywordsNode) {
                            if (keyword.isTextual() && !keyword.asText().isBlank()) {
                                keywords.add(keyword.asText().toLowerCase(java.util.Locale.ROOT));
                            }
                        }
                    }
                    if (!label.isBlank() && !keywords.isEmpty()) {
                        rules.add(new KeywordRule(label, keywords));
                    }
                }
            }
            if (!rules.isEmpty()) {
                return rules;
            }
        } catch (Exception e) {
            log.warn("解析AI关键词规则失败，使用默认内置规则: {}", e.getMessage());
        }
        return "category".equals(labelField)
            ? parseKeywordRules(DEFAULT_AI_CATEGORY_KEYWORDS, labelField)
            : parseKeywordRules(DEFAULT_AI_URGENCY_RULES, labelField);
    }

    private String internalSecret() {
        if (configuredInternalSecret != null && !configuredInternalSecret.isBlank()) {
            return configuredInternalSecret;
        }
        String envSecret = System.getenv("INTERNAL_SERVICE_SECRET");
        return envSecret == null ? "" : envSecret;
    }

    public record AiRules(List<KeywordRule> categoryRules, List<KeywordRule> urgencyRules) {}

    public record KeywordRule(String result, List<String> keywords) {}
}
