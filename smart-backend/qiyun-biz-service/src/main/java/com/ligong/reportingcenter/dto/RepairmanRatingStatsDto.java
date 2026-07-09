package com.ligong.reportingcenter.dto;

public record RepairmanRatingStatsDto(
    String id,
    String name,
    int rating,
    int completedOrders
) {
}