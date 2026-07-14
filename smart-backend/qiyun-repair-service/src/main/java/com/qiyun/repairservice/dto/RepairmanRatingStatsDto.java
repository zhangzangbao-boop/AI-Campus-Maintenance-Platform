package com.qiyun.repairservice.dto;

public record RepairmanRatingStatsDto(
    String id,
    String name,
    int rating,
    int completedOrders
) {
}