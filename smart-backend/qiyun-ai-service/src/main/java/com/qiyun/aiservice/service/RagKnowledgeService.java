package com.qiyun.aiservice.service;

import com.qiyun.aiservice.config.ChromaConfig;
import com.qiyun.aiservice.config.DeepSeekConfig;
import com.qiyun.aiservice.service.ChromaClientService.RetrievalResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * RAG 知识库服务
 * 基于 Chroma 向量检索的问答服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagKnowledgeService {

    private final ChromaClientService chromaClientService;
    private final DeepSeekClientService deepSeekClientService;
    private final ChromaConfig chromaConfig;
    private final DeepSeekConfig deepSeekConfig;

    /**
     * RAG 问答接口
     *
     * @param question    用户问题
     * @param categoryKey 故障分类（可选）
     * @return 问答结果
     */
    public RagAnswer ask(String question, String categoryKey) {
        if (question == null || question.isBlank()) {
            return RagAnswer.noAnswer("问题不能为空");
        }

        // 检查 Chroma 是否可用
        if (!chromaClientService.isAvailable()) {
            log.warn("Chroma 服务不可用，无法进行 RAG 检索");
            return RagAnswer.fallback("向量知识库暂不可用，请联系管理员");
        }

        // 确保集合存在
        if (!chromaClientService.ensureCollection()) {
            log.error("无法初始化 Chroma 集合");
            return RagAnswer.fallback("知识库初始化失败，请联系管理员");
        }

        // 构建过滤条件
        Map<String, String> whereFilter = null;
        if (categoryKey != null && !categoryKey.isBlank()) {
            whereFilter = new HashMap<>();
            whereFilter.put("categoryKey", categoryKey);
        }

        // 检索相关文档
        int topK = chromaConfig.getTopK();
        List<RetrievalResult> results = chromaClientService.query(question, topK, whereFilter);

        if (results.isEmpty()) {
            log.info("RAG 检索无匹配结果: question={}", question);
            return RagAnswer.noMatch("未找到相关维修知识，请联系管理员或提交报修工单");
        }

        // 过滤低相似度结果
        List<RetrievalResult> relevantResults = results.stream()
            .filter(r -> r.similarity() >= 0.3)
            .toList();

        if (relevantResults.isEmpty()) {
            log.info("RAG 检索无高相似度匹配: question={}, maxSimilarity={}",
                question, results.get(0).similarity());
            return RagAnswer.noMatch("未找到相关维修知识，请联系管理员或提交报修工单");
        }

        // 尝试使用 AI 生成回答
        if (deepSeekClientService.isAvailable()) {
            String answer = generateAiAnswer(question, relevantResults);
            if (answer != null && !answer.isBlank()) {
                return RagAnswer.success(answer, relevantResults, false);
            }
        }

        // AI 不可用，生成降级回答
        String fallbackAnswer = generateFallbackAnswer(question, relevantResults);
        return RagAnswer.success(fallbackAnswer, relevantResults, true);
    }

    /**
     * 使用 AI 生成回答
     */
    private String generateAiAnswer(String question, List<RetrievalResult> results) {
        try {
            StringBuilder context = new StringBuilder();
            context.append("以下是相关维修知识：\n\n");
            for (int i = 0; i < results.size(); i++) {
                RetrievalResult r = results.get(i);
                context.append("【知识").append(i + 1).append("】\n");
                context.append(r.document()).append("\n\n");
            }

            String systemPrompt = buildRagSystemPrompt();
            String userPrompt = context + "\n\n用户问题：" + question + "\n\n请根据上述知识回答用户问题。";

            // 使用 DeepSeekClientService 的 chat 方法（需要扩展）
            return callDeepSeek(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.error("AI 生成回答失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用 DeepSeek API
     */
    private String callDeepSeek(String systemPrompt, String userPrompt) {
        try {
            // 复用 DeepSeekClientService 的逻辑
            // 由于 chat 方法是 private，我们需要在这里重新实现
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

            Map<String, Object> body = new HashMap<>();
            body.put("model", deepSeekConfig.getModel());
            body.put("stream", false);
            body.put("temperature", 0.4);
            body.put("max_tokens", 500);

            List<Map<String, String>> messages = new java.util.ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));
            body.put("messages", messages);

            String baseUrl = deepSeekConfig.getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.deepseek.com";
            }
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(baseUrl + "/chat/completions"))
                .timeout(java.time.Duration.ofSeconds(Math.max(deepSeekConfig.getTimeoutSeconds(), 10)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + deepSeekConfig.getApiKey())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body)))
                .build();

            java.net.http.HttpResponse<String> response = httpClient.send(
                request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            // 提取内容
            Map<String, Object> responseMap = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                response.body(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Object choicesObj = responseMap.get("choices");
            if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
                Object firstChoice = choices.get(0);
                if (firstChoice instanceof Map<?, ?> choiceMap) {
                    Object messageObj = choiceMap.get("message");
                    if (messageObj instanceof Map<?, ?> messageMap) {
                        Object content = messageMap.get("content");
                        return content == null ? null : String.valueOf(content).trim();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("调用 DeepSeek API 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 生成降级回答（AI 不可用时）
     */
    private String generateFallbackAnswer(String question, List<RetrievalResult> results) {
        if (results.size() == 1) {
            RetrievalResult r = results.get(0);
            return "根据维修知识库：\n\n" + r.document() + "\n\n（相似度：" + formatSimilarity(r.similarity()) + "）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("根据维修知识库，找到以下相关知识：\n\n");

        for (int i = 0; i < Math.min(results.size(), 3); i++) {
            RetrievalResult r = results.get(i);
            sb.append("• ").append(truncate(r.document(), 200)).append("\n\n");
        }

        sb.append("建议：请参考上述知识进行处理，或联系维修人员。");
        return sb.toString();
    }

    /**
     * 构建 RAG 系统提示词
     */
    private String buildRagSystemPrompt() {
        return "你是校园维修智能助手。请根据提供的维修知识，简洁准确地回答用户问题。\n"
            + "回答要求：\n"
            + "1. 仅基于提供的知识回答，不要编造信息\n"
            + "2. 如果知识中没有相关信息，明确说明\n"
            + "3. 回答简洁，不超过200字\n"
            + "4. 如涉及安全注意事项，请提醒用户";
    }

    /**
     * 格式化相似度
     */
    private String formatSimilarity(double similarity) {
        return String.format("%.0f%%", similarity * 100);
    }

    /**
     * 截断文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * RAG 回答结果
     */
    public record RagAnswer(
        String answer,
        List<Source> sources,
        double maxSimilarity,
        boolean fallback,
        boolean success,
        String message
    ) {
        public record Source(
            String id,
            String title,
            String categoryKey,
            String snippet,
            double similarity
        ) {}

        public static RagAnswer success(String answer, List<RetrievalResult> results, boolean fallback) {
            List<Source> sources = results.stream()
                .map(r -> new Source(
                    r.id(),
                    r.metadata().getOrDefault("title", "未知标题"),
                    r.metadata().getOrDefault("categoryKey", ""),
                    truncate(r.document(), 100),
                    r.similarity()
                ))
                .toList();

            double maxSimilarity = results.stream()
                .mapToDouble(RetrievalResult::similarity)
                .max()
                .orElse(0);

            return new RagAnswer(answer, sources, maxSimilarity, fallback, true, "回答生成成功");
        }

        public static RagAnswer noAnswer(String message) {
            return new RagAnswer(null, List.of(), 0, false, false, message);
        }

        public static RagAnswer noMatch(String message) {
            return new RagAnswer(null, List.of(), 0, false, false, message);
        }

        public static RagAnswer fallback(String message) {
            return new RagAnswer(null, List.of(), 0, true, false, message);
        }

        private static String truncate(String text, int maxLength) {
            if (text == null) {
                return "";
            }
            if (text.length() <= maxLength) {
                return text;
            }
            return text.substring(0, maxLength) + "...";
        }
    }
}