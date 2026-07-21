package com.qiyun.aiservice.dto;

import java.util.List;

/**
 * AI 分析工单响应 DTO
 */
public record AnalyzeTicketResponse(
    String category,      // 故障类型
    String urgency,       // 紧急程度
    String suggestion,    // 维修建议
    List<String> keywords, // 关键词列表
    String title,         // 报修标题
    String locationText,  // 识别到的具体位置
    String location,      // 兼容前端旧字段
    String source         // deepseek / rules
) {}
