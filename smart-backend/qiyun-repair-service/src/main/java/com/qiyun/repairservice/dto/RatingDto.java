package com.qiyun.repairservice.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RatingDto(
    Long ratingId,
    Integer score,
    String comment,
    String studentId,
    String studentName,
    String staffId,
    String staffName,
    Long repairOrderId,
    Integer speedRating,
    Integer qualityRating,
    Integer attitudeRating,
    Boolean resolved,
    Boolean anonymous,
    LocalDateTime ratedAt,
    String sentiment,
    Double sentimentScore,
    List<String> sentimentKeywords,
    String sentimentSummary,
    LocalDateTime sentimentAnalyzedAt,
    String followUpStatus,
    String followUpNote,
    String followUpOperatorId,
    String followUpOperatorName,
    LocalDateTime followUpUpdatedAt
) {
}
