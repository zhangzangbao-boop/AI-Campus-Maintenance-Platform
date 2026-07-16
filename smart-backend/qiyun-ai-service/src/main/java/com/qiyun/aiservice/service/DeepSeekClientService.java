package com.qiyun.aiservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.aiservice.config.DeepSeekConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * DeepSeek API 客户端服务
 * 负责与 DeepSeek API 通信
 */
@Slf4j
@Service
public class DeepSeekClientService {

    private final DeepSeekConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekClientService(DeepSeekConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * 检查 DeepSeek 服务是否可用
     */
    public boolean isAvailable() {
        return config.isEnabled() && isConfigured();
    }

    /**
     * 检查是否已配置 API Key
     */
    public boolean isConfigured() {
        String apiKey = config.getApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 获取提供商名称
     */
    public String getProviderName() {
        return config.getProvider();
    }

    /**
     * 获取模型名称
     */
    public String getModelName() {
        return config.getModel();
    }

    /**
     * 调用 DeepSeek API 分析工单
     *
     * @param description 问题描述
     * @param location    位置信息
     * @return AI 分析结果，失败返回 null
     */
    public Map<String, Object> analyzeTicket(String description, String location) {
        if (!isAvailable()) {
            log.debug("DeepSeek AI 未启用或未配置");
            return null;
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(description, location);

        try {
            String response = chat(systemPrompt, userPrompt, true, 800);
            if (response == null || response.isBlank()) {
                log.warn("DeepSeek API 返回空响应");
                return null;
            }
            return parseJsonResponse(response);
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 发送聊天请求
     */
    private String chat(String systemPrompt, String userPrompt, boolean jsonMode, int maxTokens) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.getModel());
            body.put("stream", false);
            body.put("temperature", jsonMode ? 0.2 : 0.4);
            body.put("max_tokens", maxTokens <= 0 ? 800 : maxTokens);

            if (jsonMode) {
                body.put("response_format", Map.of("type", "json_object"));
            }

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));
            body.put("messages", messages);

            String baseUrl = normalizeBaseUrl(config.getBaseUrl());
            String apiKey = config.getApiKey();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(Math.max(config.getTimeoutSeconds(), 5)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("DeepSeek API 请求失败，状态码: {}", response.statusCode());
                return null;
            }

            return extractContent(response.body());
        } catch (Exception e) {
            log.error("DeepSeek API 调用异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 API 响应中提取内容
     */
    private String extractContent(String responseBody) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(
                responseBody,
                new TypeReference<Map<String, Object>>() {}
            );

            Object choicesObj = responseMap.get("choices");
            if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                return null;
            }

            Object firstChoice = choices.get(0);
            if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
                return null;
            }

            Object messageObj = choiceMap.get("message");
            if (!(messageObj instanceof Map<?, ?> messageMap)) {
                return null;
            }

            Object content = messageMap.get("content");
            return content == null ? null : String.valueOf(content).trim();
        } catch (Exception e) {
            log.error("解析 DeepSeek API 响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 JSON 响应
     */
    private Map<String, Object> parseJsonResponse(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String json = content.trim();
        // 提取 JSON 对象
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析 JSON 响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return "你是校园报修系统的智能分析助手。请根据学生描述的问题，分析并输出 JSON 格式的结果。\n"
            + "字段要求：\n"
            + "- category: 故障分类，可选值：空调故障、管道故障、电力故障、网络故障、家具故障、门窗故障、其他故障\n"
            + "- urgency: 紧急程度，可选值：紧急、普通、一般\n"
            + "- suggestion: 简洁的维修建议（不超过100字）\n"
            + "- keywords: 关键词数组，提取3-5个关键描述词\n\n"
            + "紧急程度判断规则：\n"
            + "- 紧急：涉及安全（漏水、积水、触电、烧焦、冒烟、火花、异味、消防问题）\n"
            + "- 普通：影响正常使用（无法使用、断线、损坏、堵塞）\n"
            + "- 一般：轻微问题或不影响使用\n\n"
            + "只输出 JSON 对象，不要输出其他内容。";
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String description, String location) {
        StringBuilder sb = new StringBuilder();
        sb.append("问题描述：").append(description);
        if (location != null && !location.isBlank()) {
            sb.append("\n位置信息：").append(location);
        }
        sb.append("\n\n请分析并输出 JSON 格式的结果。");
        return sb.toString();
    }

    /**
     * 标准化基础 URL
     */
    private String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "https://api.deepseek.com";
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}