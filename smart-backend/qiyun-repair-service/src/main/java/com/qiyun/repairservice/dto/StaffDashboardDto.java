package com.qiyun.repairservice.dto;

import java.util.Map;

/**
 * 维修工工作台统计数据 DTO
 */
public record StaffDashboardDto(
    Long totalTaskCount,
    // 待处理工单数量（WAITING_ACCEPT）
    Long pendingCount,
    // 进行中工单数量（IN_PROGRESS）
    Long inProgressCount,
    Long awaitingFeedbackCount,
    Long finishedCount,
    // 已完成工单数量（RESOLVED + WAITING_FEEDBACK + FEEDBACKED + CLOSED）
    Long completedCount,
    Long todayTaskCount,
    Long highPriorityCount,
    Long slaAlertCount,
    // 今日完成数量
    Long todayCompletedCount,
    // 本周完成数量
    Long weekCompletedCount,
    // 本月完成数量
    Long monthCompletedCount,
    // 平均评分
    Double avgRating,
    // 总评价数
    Long totalRatingCount,
    // 按优先级分布
    Map<String, Long> priorityDistribution,
    // 按分类分布
    Map<String, Long> categoryDistribution,
    // 平均处理时长（小时）
    Double avgProcessingHours
) {
}
