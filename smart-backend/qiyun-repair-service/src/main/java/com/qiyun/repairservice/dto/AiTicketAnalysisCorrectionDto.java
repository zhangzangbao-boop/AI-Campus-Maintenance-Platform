package com.qiyun.repairservice.dto;

import java.time.LocalDateTime;

public record AiTicketAnalysisCorrectionDto(
    Long correctionId,
    String previousCategoryKey,
    String previousUrgency,
    String previousSuggestion,
    String newCategoryKey,
    String newUrgency,
    String newSuggestion,
    String reason,
    String correctedBy,
    LocalDateTime correctedAt
) {
}
