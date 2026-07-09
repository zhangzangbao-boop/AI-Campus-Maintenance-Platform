package com.ligong.reportingcenter.service;

import com.ligong.reportingcenter.domain.entity.Category;
import com.ligong.reportingcenter.domain.entity.KnowledgeBase;
import com.ligong.reportingcenter.dto.KnowledgeBaseDto;
import com.ligong.reportingcenter.dto.request.KnowledgeBaseRequest;
import com.ligong.reportingcenter.exception.BusinessException;
import com.ligong.reportingcenter.repository.KnowledgeBaseRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final CategoryService categoryService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<KnowledgeBaseDto> list(String categoryKey, String keyword, boolean includeDisabled) {
        List<KnowledgeBase> rows = includeDisabled
            ? knowledgeBaseRepository.findAllWithCategory()
            : knowledgeBaseRepository.searchEnabled(categoryKey, keyword);
        return rows.stream()
            .filter(item -> includeDisabled || Boolean.TRUE.equals(item.getEnabled()))
            .filter(item -> categoryKey == null || categoryKey.isBlank()
                || (item.getCategory() != null && categoryKey.equals(item.getCategory().getCategoryName())))
            .filter(item -> keyword == null || keyword.isBlank() || matches(item, keyword))
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseDto> recommend(String categoryKey, String text, int limit) {
        String keyword = text == null ? "" : text;
        int size = limit <= 0 ? 5 : Math.min(limit, 10);
        return knowledgeBaseRepository.findEnabledByCategory(categoryKey).stream()
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
        auditLogService.record("知识库", "新增知识库", "KNOWLEDGE", String.valueOf(item.getKnowledgeId()), item.getTitle());
        return toDto(item);
    }

    @Transactional
    public KnowledgeBaseDto update(Long id, KnowledgeBaseRequest request) {
        KnowledgeBase item = knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "知识库条目不存在"));
        apply(item, request);
        knowledgeBaseRepository.save(item);
        auditLogService.record("知识库", "更新知识库", "KNOWLEDGE", String.valueOf(id), item.getTitle());
        return toDto(item);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeBase item = knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "知识库条目不存在"));
        knowledgeBaseRepository.delete(item);
        auditLogService.record("知识库", "删除知识库", "KNOWLEDGE", String.valueOf(id), item.getTitle());
    }

    private void apply(KnowledgeBase item, KnowledgeBaseRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new BusinessException("知识库标题不能为空");
        }
        item.setTitle(request.title());
        item.setSymptomKeywords(request.symptomKeywords());
        item.setSolutionSteps(request.solutionSteps());
        item.setSafetyNotes(request.safetyNotes());
        item.setEstimatedMinutes(request.estimatedMinutes());
        item.setEnabled(request.enabled() == null || request.enabled());
        if (request.categoryKey() != null && !request.categoryKey().isBlank()) {
            Category category = categoryService.getByName(request.categoryKey());
            item.setCategory(category);
        }
    }

    private boolean matches(KnowledgeBase item, String keyword) {
        String lower = keyword.toLowerCase();
        return contains(item.getTitle(), lower)
            || contains(item.getSymptomKeywords(), lower)
            || contains(item.getSolutionSteps(), lower)
            || contains(item.getSafetyNotes(), lower);
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
            item.getCategory() != null ? item.getCategory().getCategoryName() : null,
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
