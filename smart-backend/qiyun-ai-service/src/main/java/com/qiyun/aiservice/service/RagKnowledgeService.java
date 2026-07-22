package com.qiyun.aiservice.service;

import com.qiyun.aiservice.config.ChromaConfig;
import com.qiyun.aiservice.service.ChromaClientService.RetrievalResult;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    private static final String KNOWLEDGE_TYPE = "knowledge-base";
    private static final String NO_MATCH_MESSAGE = "未检索到相关维修知识，建议提交报修，由维修人员现场确认处理";
    private static final String EMPTY_INDEX_MESSAGE =
        "知识向量索引为空，请等待自动同步或在管理端重建索引；建议提交报修，由维修人员现场确认处理";
    private static final Map<String, Set<String>> CATEGORY_ALIASES = Map.ofEntries(
        Map.entry("ac", Set.of("ac", "空调维修", "空调故障", "电器故障")),
        Map.entry("plumbing", Set.of("plumbing", "水电维修", "管道故障")),
        Map.entry("electrical", Set.of("electrical", "电器故障", "电力故障", "水电维修")),
        Map.entry("network", Set.of("network", "网络故障")),
        Map.entry("furniture", Set.of("furniture", "家具维修", "家具故障")),
        Map.entry("door_window", Set.of("door_window", "门窗维修", "门窗故障", "公共设施")),
        Map.entry("elevator", Set.of("elevator", "公共设施")),
        Map.entry("fire_safety", Set.of("fire_safety", "消防安全")),
        Map.entry("public_facility", Set.of("public_facility", "公共设施", "卫生清洁")),
        Map.entry("multimedia", Set.of("multimedia", "电器故障", "公共设施")),
        Map.entry("lab_safety", Set.of("lab_safety", "电器故障", "水电维修", "公共设施", "消防安全")),
        Map.entry("other", Set.of("other", "其他", "其他故障", "公共设施", "卫生清洁", "消防安全"))
    );

    private final ChromaClientService chromaClientService;
    private final DeepSeekClientService deepSeekClientService;
    private final EmbeddingService embeddingService;
    private final ChromaConfig chromaConfig;
    private final LocalKnowledgeIndexService localKnowledgeIndexService;

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
        String normalizedQuestion = question.trim();

        if (!embeddingService.isAvailable()) {
            log.warn("Embedding 模型不可用，切换到本地知识索引检索");
            return askLocalIndex(normalizedQuestion, categoryKey, "Embedding 模型不可用，已使用本地知识库索引检索");
        }

        // 检查 Chroma 是否可用
        if (!chromaClientService.isAvailable()) {
            log.warn("Chroma 服务不可用，切换到本地知识索引检索");
            return askLocalIndex(question, categoryKey, "向量知识库不可用，已使用本地知识库索引检索");
        }

        // 确保集合存在
        if (!chromaClientService.ensureCollection()) {
            log.error("无法初始化 Chroma 集合");
            return askLocalIndex(question, categoryKey, "向量知识库初始化失败，已使用本地知识库索引检索");
        }

        // 生成查询 embedding
        float[] queryEmbedding = embeddingService.embed(normalizedQuestion);
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            log.error("生成查询 Embedding 失败: question={}", normalizedQuestion);
            return askLocalIndex(normalizedQuestion, categoryKey, "向量生成失败，已使用本地知识库索引检索");
        }

        // 校验维度
        if (queryEmbedding.length != embeddingService.getDimensions()) {
            log.error("查询 Embedding 维度不匹配: expected={}, actual={}",
                embeddingService.getDimensions(), queryEmbedding.length);
            return askLocalIndex(normalizedQuestion, categoryKey, "向量维度不匹配，已使用本地知识库索引检索");
        }

        // 构建过滤条件
        Map<String, String> whereFilter = new HashMap<>();
        whereFilter.put("type", KNOWLEDGE_TYPE);

        // 检索相关文档
        int topK = chromaConfig.getTopK();
        List<RetrievalResult> results = chromaClientService.queryWithEmbedding(queryEmbedding, topK, whereFilter);

        if (results.isEmpty()) {
            log.info("RAG 检索无匹配结果: question={}", normalizedQuestion);
            String noMatchMessage = chromaClientService.countDocuments() == 0
                ? EMPTY_INDEX_MESSAGE
                : NO_MATCH_MESSAGE;
            return RagAnswer.noMatch(noMatchMessage);
        }

        // 过滤低相似度结果
        double threshold = resolveSimilarityThreshold();
        List<RetrievalResult> relevantResults = results.stream()
            .filter(this::isKnowledgeBaseResult)
            .filter(r -> categoryMatches(categoryKey, r.metadata().getOrDefault("categoryKey", "")))
            .filter(r -> r.similarity() >= threshold)
            .toList();

        if (relevantResults.isEmpty()) {
            log.info("RAG 检索无高相似度匹配: question={}, maxSimilarity={}",
                normalizedQuestion, results.get(0).similarity());
            return RagAnswer.noMatch(NO_MATCH_MESSAGE);
        }

        // 尝试使用 AI 生成回答
        if (deepSeekClientService.isAvailable()) {
            String answer = generateAiAnswer(normalizedQuestion, relevantResults);
            if (answer != null && !answer.isBlank()) {
                return RagAnswer.success(answer, relevantResults, false, "chroma_ai");
            }
        }

        // AI 不可用，生成降级回答
        String fallbackAnswer = generateFallbackAnswer(normalizedQuestion, relevantResults);
        return RagAnswer.success(fallbackAnswer, relevantResults, true, "chroma_basic");
    }

    private RagAnswer askLocalIndex(String question, String categoryKey, String reason) {
        return askLocalIndex(question, categoryKey, reason, NO_MATCH_MESSAGE);
    }

    private RagAnswer askLocalIndex(String question, String categoryKey, String reason, String noMatchMessage) {
        int topK = chromaConfig.getTopK() > 0 ? chromaConfig.getTopK() : 5;
        List<RetrievalResult> localResults = localKnowledgeIndexService.search(question, topK, categoryKey);
        if (localResults == null || localResults.isEmpty()) {
            log.info("本地知识索引无匹配结果: question={}, categoryKey={}, indexed={}",
                question, categoryKey, localKnowledgeIndexService.size());
            return RagAnswer.noMatch(noMatchMessage);
        }

        String answer = generateFallbackAnswer(question, localResults);
        return RagAnswer.success(answer + "\n\n（" + reason + "）", localResults, true, "local_index");
    }

    private double resolveSimilarityThreshold() {
        double threshold = chromaConfig.getSimilarityThreshold();
        if (threshold <= 0 || threshold > 1) {
            return 0.3;
        }
        return threshold;
    }

    private boolean isKnowledgeBaseResult(RetrievalResult result) {
        String type = result.metadata().get("type");
        return type == null || type.isBlank() || KNOWLEDGE_TYPE.equals(type);
    }

    private boolean categoryMatches(String requestedCategory, String resultCategory) {
        if (requestedCategory == null || requestedCategory.isBlank()) {
            return true;
        }
        if (resultCategory == null || resultCategory.isBlank()) {
            return false;
        }

        String requested = requestedCategory.trim();
        String actual = resultCategory.trim();
        if (requested.equalsIgnoreCase(actual)) {
            return true;
        }

        Set<String> aliases = CATEGORY_ALIASES.get(requested.toLowerCase(Locale.ROOT));
        return aliases != null && aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(actual));
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
                context.append("标题：").append(r.metadata().getOrDefault("title", "未知标题")).append("\n");
                context.append("相似度：").append(formatSimilarity(r.similarity())).append("\n");
                context.append(r.document()).append("\n\n");
            }

            String systemPrompt = buildRagSystemPrompt();
            String userPrompt = context + "\n\n用户问题：" + question + "\n\n请根据上述知识回答用户问题。";

            return deepSeekClientService.requestText(systemPrompt, userPrompt, 500);
        } catch (Exception e) {
            log.error("AI 生成回答失败: {}", e.getMessage());
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
        String retrievalMode,
        boolean success,
        String message
    ) {
        public record Source(
            String id,
            String title,
            String categoryKey,
            String snippet,
            String content,
            double similarity
        ) {}

        public static RagAnswer success(String answer, List<RetrievalResult> results, boolean fallback, String retrievalMode) {
            List<Source> sources = results.stream()
                .map(r -> new Source(
                    r.id(),
                    r.metadata().getOrDefault("title", "未知标题"),
                    r.metadata().getOrDefault("categoryKey", ""),
                    r.document(),
                    r.document(),
                    r.similarity()
                ))
                .toList();

            double maxSimilarity = results.stream()
                .mapToDouble(RetrievalResult::similarity)
                .max()
                .orElse(0);

            return new RagAnswer(answer, sources, maxSimilarity, fallback, retrievalMode, true, "回答生成成功");
        }

        public static RagAnswer noAnswer(String message) {
            return new RagAnswer(null, List.of(), 0, false, "none", false, message);
        }

        public static RagAnswer noMatch(String message) {
            return new RagAnswer(null, List.of(), 0, false, "none", false, message);
        }

        public static RagAnswer fallback(String message) {
            return new RagAnswer(null, List.of(), 0, true, "fallback", false, message);
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
