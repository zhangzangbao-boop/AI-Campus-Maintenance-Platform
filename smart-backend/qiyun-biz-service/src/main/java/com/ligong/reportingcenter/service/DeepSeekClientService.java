package com.ligong.reportingcenter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligong.reportingcenter.domain.entity.SystemConfig;
import com.ligong.reportingcenter.repository.SystemConfigRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeepSeekClientService {

    private final ObjectMapper objectMapper;
    private final SystemConfigRepository systemConfigRepository;
    private final Environment environment;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    @Value("${ai.enabled:false}")
    private boolean enabled;

    @Value("${ai.provider:deepseek}")
    private String provider;

    @Value("${ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    @Value("${ai.timeout-seconds:20}")
    private int timeoutSeconds;

    @Value("${ai.thinking.enabled:false}")
    private boolean thinkingEnabled;

    public boolean available() {
        return enabled() && configured();
    }

    public boolean enabled() {
        return configBoolean("ai.enabled", enabled);
    }

    public boolean configured() {
        return !configValue("ai.api-key", apiKey).isBlank();
    }

    public String providerName() {
        return configValue("ai.provider", provider);
    }

    public String modelName() {
        return configValue("ai.model", model);
    }

    public Map<String, Object> requestJson(String systemPrompt, String userPrompt, int maxTokens) {
        if (!available()) {
            return Map.of();
        }
        String content = chat(systemPrompt, userPrompt, true, maxTokens);
        return parseJsonObject(content);
    }

    public String requestText(String systemPrompt, String userPrompt, int maxTokens) {
        if (!available()) {
            return "";
        }
        return chat(systemPrompt, userPrompt, false, maxTokens);
    }

    private String chat(String systemPrompt, String userPrompt, boolean jsonMode, int maxTokens) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", configValue("ai.model", model));
            body.put("stream", false);
            body.put("temperature", jsonMode ? 0.2 : 0.4);
            body.put("max_tokens", maxTokens <= 0 ? 800 : maxTokens);

            if (configBoolean("ai.thinking.enabled", thinkingEnabled)) {
                body.put("thinking", Map.of("type", "enabled"));
            }

            if (jsonMode) {
                body.put("response_format", Map.of("type", "json_object"));
            }

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));
            body.put("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(Math.max(configInt("ai.timeout-seconds", timeoutSeconds), 5)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + configValue("ai.api-key", apiKey))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "";
            }
            Map<String, Object> responseMap = objectMapper.readValue(
                response.body(),
                new TypeReference<Map<String, Object>>() {}
            );
            Object choicesObj = responseMap.get("choices");
            if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                return "";
            }
            Object firstChoice = choices.get(0);
            if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
                return "";
            }
            Object messageObj = choiceMap.get("message");
            if (!(messageObj instanceof Map<?, ?> messageMap)) {
                return "";
            }
            Object content = messageMap.get("content");
            return content == null ? "" : String.valueOf(content).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private Map<String, Object> parseJsonObject(String content) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        String json = content.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String normalizeBaseUrl() {
        String url = baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : baseUrl.trim();
        url = configValue("ai.base-url", url);
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String configValue(String key, String fallback) {
        String runtimeValue = runtimeAiConfigValue(key);
        if (runtimeValue != null && !runtimeValue.isBlank()) {
            return runtimeValue;
        }
        try {
            return systemConfigRepository.findById(key)
                .map(SystemConfig::getConfigValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse(fallback == null ? "" : fallback);
        } catch (Exception ignored) {
            return fallback == null ? "" : fallback;
        }
    }

    private String runtimeAiConfigValue(String key) {
        String envKey = switch (key) {
            case "ai.enabled" -> "AI_ENABLED";
            case "ai.provider" -> "AI_PROVIDER";
            case "ai.base-url" -> "AI_BASE_URL";
            case "ai.api-key" -> "DEEPSEEK_API_KEY";
            case "ai.model" -> "AI_MODEL";
            case "ai.timeout-seconds" -> "AI_TIMEOUT_SECONDS";
            case "ai.thinking.enabled" -> "AI_THINKING_ENABLED";
            default -> null;
        };
        if (envKey == null) {
            return null;
        }
        String value = environment.getProperty(envKey);
        return value == null ? null : value.trim();
    }

    private boolean configBoolean(String key, boolean fallback) {
        String value = configValue(key, String.valueOf(fallback));
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private int configInt(String key, int fallback) {
        try {
            return Integer.parseInt(configValue(key, String.valueOf(fallback)));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
