package com.ligong.reportingcenter.dto;

import java.util.List;
import java.util.Map;

/**
 * 管理员智慧分析仪表盘数据DTO
 */
public record AnalysisDashboardDto(
    // 概览统计
    long totalTickets,
    long pendingTickets,
    long inProgressTickets,
    long resolvedTickets,
    long closedTickets,

    // 效率指标
    double avgProcessingHours,
    String avgProcessingDisplay,
    double slaComplianceRate,

    // 故障类型分布
    List<Map<String, Object>> categoryDistribution,

    // 高频故障（Top N）
    List<Map<String, Object>> highFrequencyFaults,

    // 月度趋势
    List<Map<String, Object>> monthlyTrend,

    // 维修效率排行
    List<Map<String, Object>> efficiencyRanking,

    // 预留：AI增强分析结果（后续接入大模型时使用）
    String aiAnalysisStatus,
    Map<String, Object> aiInsights
) {
}
