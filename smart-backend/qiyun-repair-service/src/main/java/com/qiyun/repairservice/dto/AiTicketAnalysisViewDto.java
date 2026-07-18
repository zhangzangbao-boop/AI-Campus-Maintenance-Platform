package com.qiyun.repairservice.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AiTicketAnalysisViewDto(
    Long analysisId,
    Long ticketId,
    String originalCategoryKey,
    String originalUrgency,
    String originalSuggestion,
    String finalCategoryKey,
    String finalUrgency,
    String finalSuggestion,
    String correctionReason,
    String correctedBy,
    LocalDateTime correctedAt,
    String provider,
    String model,
    String rawResponse,
    LocalDateTime createdAt,
    List<AiTicketAnalysisCorrectionDto> corrections
) {
}
