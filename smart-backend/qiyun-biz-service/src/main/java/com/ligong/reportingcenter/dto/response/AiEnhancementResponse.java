package com.ligong.reportingcenter.dto.response;

import java.util.Map;

/**
 * AI增强分析响应 - 预留接口，用于后续接入大模型/知识库
 */
public record AiEnhancementResponse(
    String analysisType,
    String status,          // READY（可调用）/ PENDING（待配置）/ UNAVAILABLE（不可用）
    String message,
    Map<String, Object> result,     // 分析结果
    String knowledgeBaseUsed,       // 使用的知识库条目（如有）
    String modelInfo               // 模型信息（后续配置时填充）
) {
}
