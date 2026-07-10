package com.qiyun.feign.dto;

import java.util.List;

/**
 * AI 分析工单响应 DTO
 */
public record AnalyzeTicketResponse(
    String category,      // 故障类型
    String urgency,       // 紧急程度
    String suggestion,    // 维修建议
    List<String> keywords // 关键词列表
) {}