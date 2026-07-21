package com.qiyun.aiservice.service;

import com.qiyun.aiservice.service.ChromaClientService.RetrievalResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 零安装知识库索引。
 *
 * <p>当本地 ONNX embedding 模型或外部向量库不可用时，仍然基于管理端同步过来的
 * 维修知识条目完成检索和问答。它不是向量库的替代品，而是开发和演示环境的可用兜底。</p>
 */
@Slf4j
@Service
public class LocalKnowledgeIndexService {

    private static final int CHUNK_SIZE = 600;
    private static final int CHUNK_OVERLAP = 80;
    private static final double MIN_SCORE = 0.08;
    private static final Map<String, Set<String>> CATEGORY_ALIASES = Map.of(
        "ac", Set.of("ac", "空调维修", "空调故障", "电器故障"),
        "plumbing", Set.of("plumbing", "水电维修", "管道故障"),
        "electrical", Set.of("electrical", "电器故障", "电力故障", "水电维修"),
        "network", Set.of("network", "网络故障"),
        "furniture", Set.of("furniture", "家具维修", "家具故障"),
        "door_window", Set.of("door_window", "公共设施", "门窗故障"),
        "other", Set.of("other", "其他故障", "公共设施")
    );

    private final Map<String, IndexedDocument> documents = new ConcurrentHashMap<>();

    public void upsertKnowledge(String id, String document, Map<String, String> metadata) {
        if (id == null || id.isBlank() || document == null || document.isBlank()) {
            return;
        }

        boolean enabled = metadata == null
            || !"false".equalsIgnoreCase(metadata.getOrDefault("enabled", "true"));
        if (!enabled) {
            deleteKnowledge(id);
            return;
        }

        IndexedDocument indexed = new IndexedDocument(
            id,
            document,
            metadata == null ? Map.of() : new HashMap<>(metadata),
            splitIntoChunks(document)
        );
        documents.put(id, indexed);
        log.info("本地知识索引已更新: id={}, chunks={}", id, indexed.chunks().size());
    }

    public void deleteKnowledge(String id) {
        if (id != null) {
            documents.remove(id);
            log.info("本地知识索引已删除: id={}", id);
        }
    }

    public void clear() {
        documents.clear();
        log.info("本地知识索引已清空");
    }

    public int size() {
        return documents.size();
    }

    public List<RetrievalResult> search(String query, int topK, String categoryKey) {
        if (query == null || query.isBlank() || documents.isEmpty()) {
            return List.of();
        }

        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        int limit = topK <= 0 ? 5 : Math.min(topK, 10);
        List<ScoredChunk> scored = new ArrayList<>();

        for (IndexedDocument document : documents.values()) {
            String docCategory = document.metadata().getOrDefault("categoryKey", "");
            if (!categoryMatches(categoryKey, docCategory)) {
                continue;
            }

            for (int i = 0; i < document.chunks().size(); i++) {
                String chunk = document.chunks().get(i);
                double score = score(query, queryTokens, chunk, document.metadata());
                if (score >= MIN_SCORE) {
                    scored.add(new ScoredChunk(document, i, chunk, score));
                }
            }
        }

        return scored.stream()
            .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
            .limit(limit)
            .map(item -> new RetrievalResult(
                item.document().id() + "#chunk-" + item.chunkIndex(),
                item.chunk(),
                item.document().metadata(),
                item.score()
            ))
            .toList();
    }

    private double score(String query, Set<String> queryTokens, String chunk, Map<String, String> metadata) {
        Set<String> chunkTokens = tokenize(chunk + " " + metadata.getOrDefault("title", ""));
        if (chunkTokens.isEmpty()) {
            return 0;
        }

        Set<String> intersection = new HashSet<>(queryTokens);
        intersection.retainAll(chunkTokens);

        Set<String> union = new HashSet<>(queryTokens);
        union.addAll(chunkTokens);

        double jaccard = union.isEmpty() ? 0 : (double) intersection.size() / union.size();
        double hitRate = (double) intersection.size() / queryTokens.size();
        double score = jaccard * 0.45 + hitRate * 0.45;

        String normalizedQuery = normalize(query);
        String normalizedChunk = normalize(chunk);
        String title = normalize(metadata.getOrDefault("title", ""));
        String symptomKeywords = normalize(metadata.getOrDefault("symptomKeywords", ""));

        if (!title.isBlank() && (normalizedQuery.contains(title) || title.contains(normalizedQuery))) {
            score += 0.25;
        }
        if (!symptomKeywords.isBlank()) {
            for (String keyword : symptomKeywords.split("[,，;；\\s]+")) {
                if (!keyword.isBlank() && normalizedQuery.contains(keyword)) {
                    score += 0.12;
                }
            }
        }
        for (String token : queryTokens) {
            if (token.length() >= 2 && normalizedChunk.contains(token)) {
                score += 0.02;
            }
        }

        return Math.min(1.0, score);
    }

    private boolean categoryMatches(String requestedCategory, String documentCategory) {
        if (requestedCategory == null || requestedCategory.isBlank()) {
            return true;
        }
        if (documentCategory == null || documentCategory.isBlank()) {
            return false;
        }

        String requested = requestedCategory.trim();
        String actual = documentCategory.trim();
        if (requested.equalsIgnoreCase(actual)) {
            return true;
        }

        Set<String> aliases = CATEGORY_ALIASES.get(requested.toLowerCase(Locale.ROOT));
        return aliases != null && aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(actual));
    }

    private List<String> splitIntoChunks(String text) {
        String normalized = text.trim();
        if (normalized.length() <= CHUNK_SIZE) {
            return List.of(normalized);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + CHUNK_SIZE);
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(0, end - CHUNK_OVERLAP);
        }
        return chunks;
    }

    private Set<String> tokenize(String text) {
        String normalized = normalize(text);
        Set<String> tokens = new HashSet<>();
        if (normalized.isBlank()) {
            return tokens;
        }

        for (String part : normalized.split("[^\\p{IsHan}a-z0-9]+")) {
            if (part.isBlank()) {
                continue;
            }
            if (containsChinese(part)) {
                addChineseTokens(part, tokens);
            } else if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private void addChineseTokens(String text, Set<String> tokens) {
        if (text.length() <= 2) {
            tokens.add(text);
            return;
        }
        for (int i = 0; i < text.length() - 1; i++) {
            tokens.add(text.substring(i, i + 2));
        }
        for (int i = 0; i < text.length() - 2; i++) {
            tokens.add(text.substring(i, i + 3));
        }
    }

    private boolean containsChinese(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record IndexedDocument(
        String id,
        String document,
        Map<String, String> metadata,
        List<String> chunks
    ) {}

    private record ScoredChunk(
        IndexedDocument document,
        int chunkIndex,
        String chunk,
        double score
    ) {}
}
