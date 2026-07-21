package com.qiyun.opsservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.feign.client.AiInternalClient;
import com.qiyun.feign.client.AiInternalClient.KnowledgeSyncRequest;
import com.qiyun.opsservice.domain.entity.KnowledgeBase;
import com.qiyun.opsservice.dto.KnowledgeBaseDto;
import com.qiyun.opsservice.dto.request.KnowledgeBaseRequest;
import com.qiyun.opsservice.repository.KnowledgeBaseRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private static final String DEFAULT_INTERNAL_SECRET = "qiyun-local-internal-secret";

    private static final Map<String, Set<String>> CATEGORY_ALIASES = Map.of(
        "ac", Set.of("ac", "空调维修", "空调故障", "电器故障"),
        "plumbing", Set.of("plumbing", "水电维修", "管道故障"),
        "electrical", Set.of("electrical", "电器故障", "电力故障", "水电维修"),
        "network", Set.of("network", "网络故障"),
        "furniture", Set.of("furniture", "家具维修", "家具故障"),
        "door_window", Set.of("door_window", "公共设施", "门窗故障"),
        "other", Set.of("other", "其他故障", "公共设施")
    );

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AuditLogService auditLogService;
    private final AiInternalClient aiInternalClient;

    @Value("${internal.service.secret:}")
    private String internalSecret;

    @Transactional(readOnly = true)
    public List<KnowledgeBaseDto> list(String categoryKey, String keyword, boolean includeDisabled) {
        List<KnowledgeBase> rows = knowledgeBaseRepository.findAllWithCategory();
        return rows.stream()
            .filter(item -> includeDisabled || Boolean.TRUE.equals(item.getEnabled()))
            .filter(item -> categoryMatches(categoryKey, item.getCategoryKey()))
            .filter(item -> keyword == null || keyword.isBlank() || matches(item, keyword))
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseDto> recommend(String categoryKey, String text, int limit) {
        String keyword = text == null ? "" : text;
        int size = limit <= 0 ? 5 : Math.min(limit, 10);
        return knowledgeBaseRepository.findAllWithCategory().stream()
            .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
            .filter(item -> categoryMatches(categoryKey, item.getCategoryKey()))
            .sorted(Comparator.comparingInt((KnowledgeBase item) -> score(item, keyword)).reversed()
                .thenComparing(KnowledgeBase::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(size)
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public KnowledgeBaseDto create(KnowledgeBaseRequest request) {
        KnowledgeBase item = new KnowledgeBase();
        apply(item, request);
        knowledgeBaseRepository.save(item);

        // 同步到向量库（仅启用状态）
        if (Boolean.TRUE.equals(item.getEnabled())) {
            syncToChroma(item);
        }

        auditLogService.record("知识库", "新增知识库", "KNOWLEDGE", String.valueOf(item.getKnowledgeId()), item.getTitle());
        return toDto(item);
    }

    @Transactional
    public KnowledgeBaseDto update(Long id, KnowledgeBaseRequest request) {
        KnowledgeBase item = knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "知识库条目不存在"));

        boolean wasEnabled = Boolean.TRUE.equals(item.getEnabled());
        apply(item, request);
        knowledgeBaseRepository.save(item);

        // 同步到向量库
        if (Boolean.TRUE.equals(item.getEnabled())) {
            syncToChroma(item);
        } else if (wasEnabled) {
            // 从启用变为禁用，从向量库删除
            deleteFromChroma(item.getKnowledgeId());
        }

        auditLogService.record("知识库", "更新知识库", "KNOWLEDGE", String.valueOf(id), item.getTitle());
        return toDto(item);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeBase item = knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "知识库条目不存在"));
        knowledgeBaseRepository.delete(item);

        // 从向量库删除
        deleteFromChroma(id);

        auditLogService.record("知识库", "删除知识库", "KNOWLEDGE", String.valueOf(id), item.getTitle());
    }

    /**
     * 重建向量索引
     * 清空向量库后重新同步所有启用状态的知识条目
     */
    @Transactional
    public int rebuildIndex() {
        // 调用 AI 服务清空向量索引
        try {
            String secret = getInternalSecret();
            aiInternalClient.rebuildIndex(secret);
            log.info("向量索引已清空");
        } catch (Exception e) {
            log.error("清空向量索引失败: {}", e.getMessage());
            throw new BusinessException("清空向量索引失败: " + e.getMessage());
        }

        // 获取所有启用状态的知识条目
        List<KnowledgeBase> enabledItems = knowledgeBaseRepository.findAllWithCategory().stream()
            .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
            .toList();

        // 重新同步到向量库
        int syncedCount = 0;
        for (KnowledgeBase item : enabledItems) {
            try {
                if (syncToChroma(item)) {
                    syncedCount++;
                }
            } catch (Exception e) {
                log.warn("同步知识条目 {} 到向量库失败: {}", item.getKnowledgeId(), e.getMessage());
            }
        }

        auditLogService.record("知识库", "重建向量索引", "KNOWLEDGE", null,
            "已同步 " + syncedCount + " 条知识");
        return syncedCount;
    }

    /**
     * 同步知识条目到 Chroma
     */
    private boolean syncToChroma(KnowledgeBase item) {
        try {
            String secret = getInternalSecret();
            String document = buildDocument(item);
            KnowledgeSyncRequest request = new KnowledgeSyncRequest(
                document,
                item.getTitle(),
                item.getCategoryKey(),
                item.getEnabled()
            );
            Map<String, Object> response = aiInternalClient.syncKnowledge(secret, item.getKnowledgeId(), request);
            boolean vectorSynced = isVectorSynced(response);
            if (vectorSynced) {
                log.debug("知识条目 {} 已同步到向量库", item.getKnowledgeId());
            } else {
                log.warn("知识条目 {} 未成功写入向量库: {}", item.getKnowledgeId(), response);
            }
            return vectorSynced;
        } catch (Exception e) {
            log.error("同步知识条目 {} 到向量库失败: {}", item.getKnowledgeId(), e.getMessage());
            // 不抛出异常，避免影响主流程
            return false;
        }
    }

    private boolean isVectorSynced(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return false;
        }
        Object value = dataMap.get("vectorSynced");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    /**
     * 从 Chroma 删除知识条目
     */
    private void deleteFromChroma(Long knowledgeId) {
        try {
            String secret = getInternalSecret();
            aiInternalClient.deleteKnowledge(secret, knowledgeId);
            log.debug("知识条目 {} 已从向量库删除", knowledgeId);
        } catch (Exception e) {
            log.error("从向量库删除知识条目 {} 失败: {}", knowledgeId, e.getMessage());
        }
    }

    /**
     * 构建向量文档内容
     */
    private String buildDocument(KnowledgeBase item) {
        StringBuilder sb = new StringBuilder();
        sb.append("【标题】").append(item.getTitle()).append("\n");

        if (item.getSymptomKeywords() != null && !item.getSymptomKeywords().isBlank()) {
            sb.append("【故障症状】").append(item.getSymptomKeywords()).append("\n");
        }

        if (item.getSolutionSteps() != null && !item.getSolutionSteps().isBlank()) {
            sb.append("【解决步骤】").append(item.getSolutionSteps()).append("\n");
        }

        if (item.getSafetyNotes() != null && !item.getSafetyNotes().isBlank()) {
            sb.append("【安全提示】").append(item.getSafetyNotes()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取内部服务密钥
     */
    private String getInternalSecret() {
        String secret = (internalSecret != null && !internalSecret.isBlank())
            ? internalSecret
            : System.getenv("INTERNAL_SERVICE_SECRET");
        return (secret == null || secret.isBlank()) ? DEFAULT_INTERNAL_SECRET : secret;
    }

    private void apply(KnowledgeBase item, KnowledgeBaseRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new BusinessException("知识库标题不能为空");
        }
        item.setTitle(request.title());
        item.setCategoryKey(request.categoryKey());
        item.setSymptomKeywords(request.tags());
        item.setSolutionSteps(request.content());
        item.setEnabled(request.enabled() == null || request.enabled());
    }

    private boolean matches(KnowledgeBase item, String keyword) {
        String lower = keyword.toLowerCase();
        return contains(item.getTitle(), lower)
            || contains(item.getSymptomKeywords(), lower)
            || contains(item.getSolutionSteps(), lower);
    }

    private boolean categoryMatches(String requestedCategory, String itemCategory) {
        if (requestedCategory == null || requestedCategory.isBlank()) {
            return true;
        }
        if (itemCategory == null || itemCategory.isBlank()) {
            return false;
        }

        String requested = requestedCategory.trim();
        String actual = itemCategory.trim();
        if (requested.equalsIgnoreCase(actual)) {
            return true;
        }

        Set<String> aliases = CATEGORY_ALIASES.get(requested.toLowerCase());
        return aliases != null && aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(actual));
    }

    private int score(KnowledgeBase item, String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String lower = text.toLowerCase();
        int score = 0;
        if (contains(item.getTitle(), lower)) score += 8;
        if (contains(item.getSymptomKeywords(), lower)) score += 6;
        if (contains(item.getSolutionSteps(), lower)) score += 3;
        if (item.getSymptomKeywords() != null) {
            for (String token : item.getSymptomKeywords().split("[,，;；\\s]+")) {
                if (!token.isBlank() && lower.contains(token.toLowerCase())) {
                    score += 2;
                }
            }
        }
        return score;
    }

    private boolean contains(String value, String lowerKeyword) {
        return value != null && value.toLowerCase().contains(lowerKeyword);
    }

    private KnowledgeBaseDto toDto(KnowledgeBase item) {
        return new KnowledgeBaseDto(
            item.getKnowledgeId(),
            item.getCategoryKey(),
            item.getTitle(),
            item.getSymptomKeywords(),
            item.getSolutionSteps(),
            item.getSafetyNotes(),
            item.getEstimatedMinutes(),
            Boolean.TRUE.equals(item.getEnabled()),
            item.getCreatedAt(),
            item.getUpdatedAt()
        );
    }
}
