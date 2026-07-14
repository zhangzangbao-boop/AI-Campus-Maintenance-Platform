package com.qiyun.repairservice.dto;

public record StaffRecommendationDto(
    String staffId,
    String staffName,
    String phone,
    double score,
    int activeTaskCount,
    int sameCategoryCompletedCount,
    int completedTaskCount,
    double averageRating,
    double averageProcessingHours,
    String reason
) {
}