package com.ligong.reportingcenter.dto;

import java.time.LocalDateTime;

public record KnowledgeBaseDto(
    Long knowledgeId,
    String categoryKey,
    String title,
    String symptomKeywords,
    String solutionSteps,
    String safetyNotes,
    Integer estimatedMinutes,
    Boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
