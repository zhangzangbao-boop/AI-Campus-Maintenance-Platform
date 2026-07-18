package com.qiyun.repairservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.feign.client.AiServiceClient;
import com.qiyun.repairservice.domain.entity.AiTicketAnalysis;
import com.qiyun.repairservice.domain.entity.AiTicketAnalysisCorrection;
import com.qiyun.repairservice.dto.AiTicketAnalysisViewDto;
import com.qiyun.repairservice.dto.request.AiTicketAnalysisCorrectionRequest;
import com.qiyun.repairservice.repository.AiTicketAnalysisCorrectionRepository;
import com.qiyun.repairservice.repository.AiTicketAnalysisRepository;
import com.qiyun.repairservice.service.AiAnalysisIntegrationService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiAnalysisIntegrationServiceTests {

    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private AiTicketAnalysisRepository analysisRepository;
    @Mock
    private AiTicketAnalysisCorrectionRepository correctionRepository;

    private AiAnalysisIntegrationService service;
    private AiTicketAnalysis analysis;
    private final List<AiTicketAnalysisCorrection> history = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new AiAnalysisIntegrationService(
            aiServiceClient, analysisRepository, correctionRepository, new ObjectMapper());
        analysis = originalAnalysis();
        when(analysisRepository.findByTicketId(100L)).thenReturn(Optional.of(analysis));
        lenient().when(analysisRepository.save(any(AiTicketAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(correctionRepository.save(any(AiTicketAnalysisCorrection.class))).thenAnswer(invocation -> {
            AiTicketAnalysisCorrection correction = invocation.getArgument(0);
            correction.setCorrectionId((long) history.size() + 1);
            history.add(correction);
            return correction;
        });
        lenient().when(correctionRepository.findByAnalysisIdOrderByCorrectedAtAsc(10L)).thenAnswer(invocation -> List.copyOf(history));
    }

    @Test
    void repeatedAiAnalysisDoesNotOverwriteOriginalResult() {
        AiTicketAnalysis result = service.analyzeAndSave(100L, "new description", "new location");

        assertThat(result.getCategoryKey()).isEqualTo("AI_CATEGORY");
        assertThat(result.getRawResponse()).isEqualTo("{\"raw\":true}");
        verify(aiServiceClient, never()).analyzeTicket(any());
        verify(analysisRepository, never()).save(any());
    }

    @Test
    void multipleCorrectionsCreateAuditHistoryAndKeepOriginalFields() {
        service.correctAnalysis(100L,
            new AiTicketAnalysisCorrectionRequest("MANUAL_1", "普通", "suggestion 1", "reason 1"), "admin-1");
        AiTicketAnalysisViewDto result = service.correctAnalysis(100L,
            new AiTicketAnalysisCorrectionRequest("MANUAL_2", "一般", "suggestion 2", "reason 2"), "admin-2");

        assertThat(analysis.getCategoryKey()).isEqualTo("AI_CATEGORY");
        assertThat(analysis.getUrgency()).isEqualTo("紧急");
        assertThat(analysis.getSuggestion()).isEqualTo("AI suggestion");
        assertThat(analysis.getProvider()).isEqualTo("provider-x");
        assertThat(analysis.getModel()).isEqualTo("model-x");
        assertThat(analysis.getRawResponse()).isEqualTo("{\"raw\":true}");
        assertThat(result.finalCategoryKey()).isEqualTo("MANUAL_2");
        assertThat(result.correctedBy()).isEqualTo("admin-2");
        assertThat(result.corrections()).hasSize(2);
        assertThat(result.corrections().get(1).previousCategoryKey()).isEqualTo("MANUAL_1");
        assertThat(result.corrections().get(1).newCategoryKey()).isEqualTo("MANUAL_2");
    }

    @Test
    void nonAdminViewContainsOnlyFinalResult() {
        AiTicketAnalysisViewDto result = service.getViewByTicketId(100L, false);

        assertThat(result.finalCategoryKey()).isEqualTo("AI_CATEGORY");
        assertThat(result.finalSuggestion()).isEqualTo("AI suggestion");
        assertThat(result.originalCategoryKey()).isNull();
        assertThat(result.provider()).isNull();
        assertThat(result.model()).isNull();
        assertThat(result.rawResponse()).isNull();
        assertThat(result.corrections()).isEmpty();
    }

    private AiTicketAnalysis originalAnalysis() {
        AiTicketAnalysis value = new AiTicketAnalysis();
        value.setAnalysisId(10L);
        value.setTicketId(100L);
        value.setCategoryKey("AI_CATEGORY");
        value.setUrgency("紧急");
        value.setSuggestion("AI suggestion");
        value.setFinalCategoryKey("AI_CATEGORY");
        value.setFinalUrgency("紧急");
        value.setFinalSuggestion("AI suggestion");
        value.setProvider("provider-x");
        value.setModel("model-x");
        value.setRawResponse("{\"raw\":true}");
        value.setCreatedAt(LocalDateTime.now());
        return value;
    }
}
