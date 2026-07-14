package com.qiyun.repairservice.dto;

import java.util.List;

public record AiTicketAnalysisDto(
    String title,
    String categoryKey,
    String categoryName,
    String locationText,
    String priority,
    String summary,
    String safetyTips,
    String source,
    List<SimilarTicketDto> similarTickets,
    List<KnowledgeBaseDto> knowledgeRecommendations
) {
}