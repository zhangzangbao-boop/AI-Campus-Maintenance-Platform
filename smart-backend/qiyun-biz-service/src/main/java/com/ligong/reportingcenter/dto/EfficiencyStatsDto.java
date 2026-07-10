package com.ligong.reportingcenter.dto;

/**
 * 维修效率统计数据DTO（维修人员维度）
 */
public record EfficiencyStatsDto(
    String staffId,
    String staffName,
    long totalAssigned,       // 累计分配工单数
    long completedTickets,    // 已完成工单数
    long activeTickets,       // 当前进行中
    double completionRate,    // 完成率
    double avgProcessingHours,// 平均处理时长（小时）
    String avgProcessingDisplay,
    double avgRating,         // 平均评分
    double efficiencyScore    // 综合效率分（基于完成率、处理速度、评分加权）
) {
}
