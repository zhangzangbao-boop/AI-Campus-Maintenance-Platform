package com.ligong.reportingcenter.dto.request;

public record KnowledgeBaseRequest(
    String categoryKey,
    String title,
    String symptomKeywords,
    String solutionSteps,
    String safetyNotes,
    Integer estimatedMinutes,
    Boolean enabled
) {
}
