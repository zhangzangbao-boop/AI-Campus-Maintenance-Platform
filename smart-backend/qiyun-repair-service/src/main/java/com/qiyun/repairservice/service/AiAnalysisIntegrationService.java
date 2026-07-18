package com.qiyun.repairservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.common.exception.BusinessException;
import com.qiyun.feign.client.AiServiceClient;
import com.qiyun.feign.dto.AnalyzeTicketRequest;
import com.qiyun.feign.dto.AnalyzeTicketResponse;
import com.qiyun.repairservice.domain.entity.AiTicketAnalysis;
import com.qiyun.repairservice.domain.entity.AiTicketAnalysisCorrection;
import com.qiyun.repairservice.dto.AiTicketAnalysisCorrectionDto;
import com.qiyun.repairservice.dto.AiTicketAnalysisViewDto;
import com.qiyun.repairservice.dto.request.AiTicketAnalysisCorrectionRequest;
import com.qiyun.repairservice.repository.AiTicketAnalysisCorrectionRepository;
import com.qiyun.repairservice.repository.AiTicketAnalysisRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisIntegrationService {

    private final AiServiceClient aiServiceClient;
    private final AiTicketAnalysisRepository aiTicketAnalysisRepository;
    private final AiTicketAnalysisCorrectionRepository aiTicketAnalysisCorrectionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiTicketAnalysis analyzeAndSave(Long ticketId, String description, String location) {
        log.info("Start AI ticket analysis: ticketId={}", ticketId);

        try {
            AiTicketAnalysis analysis = aiTicketAnalysisRepository.findByTicketId(ticketId).orElseGet(AiTicketAnalysis::new);
            if (hasOriginalResult(analysis)) {
                log.info("AI ticket analysis already exists; original result remains immutable: ticketId={}", ticketId);
                return analysis;
            }

            AnalyzeTicketRequest request = new AnalyzeTicketRequest(description, location);
            Map<String, Object> responseMap = aiServiceClient.analyzeTicket(request);
            AnalyzeTicketResponse response = aiServiceClient.extractResponse(responseMap);

            if (response == null) {
                log.warn("AI ticket analysis returned empty result: ticketId={}", ticketId);
                return null;
            }

            analysis.setTicketId(ticketId);
            analysis.setSourceText(description);
            analysis.setCategoryKey(response.category());
            analysis.setLocationText(location);
            analysis.setUrgency(response.urgency());
            analysis.setSuggestion(response.suggestion());
            analysis.setFinalCategoryKey(response.category());
            analysis.setFinalUrgency(response.urgency());
            analysis.setFinalSuggestion(response.suggestion());
            analysis.setKeywords(toJsonString(response.keywords()));
            analysis.setProvider("qiyun-ai-service");
            analysis.setModel("ticket-analysis-v1");
            analysis.setRawResponse(toJsonString(responseMap));
            analysis.setPriority(mapUrgencyToPriority(response.urgency()));
            analysis.setCreatedAt(LocalDateTime.now());

            AiTicketAnalysis saved = aiTicketAnalysisRepository.save(analysis);
            log.info("AI ticket analysis saved: analysisId={}", saved.getAnalysisId());
            return saved;
        } catch (Exception e) {
            log.error("AI ticket analysis failed: ticketId={}, error={}", ticketId, e.getMessage(), e);
            return null;
        }
    }

    public AnalyzeTicketResponse analyzeOnly(String description, String location) {
        try {
            AnalyzeTicketRequest request = new AnalyzeTicketRequest(description, location);
            Map<String, Object> responseMap = aiServiceClient.analyzeTicket(request);
            return aiServiceClient.extractResponse(responseMap);
        } catch (Exception e) {
            log.error("AI ticket analysis failed: {}", e.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    public AiTicketAnalysis getByTicketId(Long ticketId) {
        return aiTicketAnalysisRepository.findByTicketId(ticketId).orElse(null);
    }

    @Transactional(readOnly = true)
    public AiTicketAnalysisViewDto getViewByTicketId(Long ticketId, boolean includeAdminFields) {
        return aiTicketAnalysisRepository.findByTicketId(ticketId)
            .map(analysis -> toViewDto(analysis, includeAdminFields))
            .orElse(null);
    }

    @Transactional
    public AiTicketAnalysisViewDto correctAnalysis(Long ticketId, AiTicketAnalysisCorrectionRequest request, String operatorId) {
        if (request == null || !hasText(request.reason())) {
            throw new BusinessException("AI analysis correction reason is required");
        }

        AiTicketAnalysis analysis = aiTicketAnalysisRepository.findByTicketId(ticketId)
            .orElseThrow(() -> new BusinessException("No AI ticket analysis found for this ticket"));

        String newCategory = firstText(request.categoryKey(), currentCategory(analysis));
        String newUrgency = firstText(request.urgency(), currentUrgency(analysis));
        String newSuggestion = firstText(request.suggestion(), currentSuggestion(analysis));
        String reason = request.reason().trim();
        LocalDateTime now = LocalDateTime.now();

        AiTicketAnalysisCorrection correction = new AiTicketAnalysisCorrection();
        correction.setAnalysisId(analysis.getAnalysisId());
        correction.setTicketId(ticketId);
        correction.setPreviousCategoryKey(currentCategory(analysis));
        correction.setPreviousUrgency(currentUrgency(analysis));
        correction.setPreviousSuggestion(currentSuggestion(analysis));
        correction.setNewCategoryKey(newCategory);
        correction.setNewUrgency(newUrgency);
        correction.setNewSuggestion(newSuggestion);
        correction.setReason(reason);
        correction.setCorrectedBy(operatorId);
        correction.setCorrectedAt(now);
        aiTicketAnalysisCorrectionRepository.save(correction);

        analysis.setFinalCategoryKey(newCategory);
        analysis.setFinalUrgency(newUrgency);
        analysis.setFinalSuggestion(newSuggestion);
        analysis.setCorrectionReason(reason);
        analysis.setCorrectedBy(operatorId);
        analysis.setCorrectedAt(now);

        return toViewDto(aiTicketAnalysisRepository.save(analysis), true);
    }

    private String mapUrgencyToPriority(String urgency) {
        if (urgency == null) {
            return "medium";
        }
        return switch (urgency) {
            case "紧急", "high", "HIGH" -> "high";
            case "一般", "low", "LOW" -> "low";
            default -> "medium";
        };
    }

    private boolean hasOriginalResult(AiTicketAnalysis analysis) {
        return analysis.getAnalysisId() != null
            && (hasText(analysis.getCategoryKey())
                || hasText(analysis.getUrgency())
                || hasText(analysis.getSuggestion())
                || hasText(analysis.getRawResponse()));
    }

    private AiTicketAnalysisViewDto toViewDto(AiTicketAnalysis analysis, boolean includeAdminFields) {
        List<AiTicketAnalysisCorrectionDto> corrections = includeAdminFields
            ? aiTicketAnalysisCorrectionRepository.findByAnalysisIdOrderByCorrectedAtAsc(analysis.getAnalysisId()).stream()
                .map(this::toCorrectionDto)
                .toList()
            : List.of();

        return new AiTicketAnalysisViewDto(
            analysis.getAnalysisId(),
            analysis.getTicketId(),
            includeAdminFields ? analysis.getCategoryKey() : null,
            includeAdminFields ? analysis.getUrgency() : null,
            includeAdminFields ? analysis.getSuggestion() : null,
            currentCategory(analysis),
            currentUrgency(analysis),
            currentSuggestion(analysis),
            includeAdminFields ? analysis.getCorrectionReason() : null,
            includeAdminFields ? analysis.getCorrectedBy() : null,
            includeAdminFields ? analysis.getCorrectedAt() : null,
            includeAdminFields ? analysis.getProvider() : null,
            includeAdminFields ? analysis.getModel() : null,
            includeAdminFields ? analysis.getRawResponse() : null,
            analysis.getCreatedAt(),
            corrections
        );
    }

    private AiTicketAnalysisCorrectionDto toCorrectionDto(AiTicketAnalysisCorrection correction) {
        return new AiTicketAnalysisCorrectionDto(
            correction.getCorrectionId(),
            correction.getPreviousCategoryKey(),
            correction.getPreviousUrgency(),
            correction.getPreviousSuggestion(),
            correction.getNewCategoryKey(),
            correction.getNewUrgency(),
            correction.getNewSuggestion(),
            correction.getReason(),
            correction.getCorrectedBy(),
            correction.getCorrectedAt()
        );
    }

    private String currentCategory(AiTicketAnalysis analysis) {
        return firstText(analysis.getFinalCategoryKey(), analysis.getCategoryKey());
    }

    private String currentUrgency(AiTicketAnalysis analysis) {
        return firstText(analysis.getFinalUrgency(), analysis.getUrgency());
    }

    private String currentSuggestion(AiTicketAnalysis analysis) {
        return firstText(analysis.getFinalSuggestion(), analysis.getSuggestion());
    }

    private String toJsonString(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize AI analysis payload: {}", e.getMessage());
            return "{}";
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
