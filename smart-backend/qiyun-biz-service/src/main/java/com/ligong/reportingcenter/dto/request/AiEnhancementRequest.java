package com.ligong.reportingcenter.dto.request;

/**
 * AI增强分析请求 - 预留接口，用于后续接入大模型/知识库
 */
public record AiEnhancementRequest(
    // 分析类型：fault_analysis（故障分析）/ efficiency_analysis（效率分析）/ trend_prediction（趋势预测）/ hotspot_analysis（热点分析）
    String analysisType,

    // 分析参数（JSON格式，根据分析类型传递不同参数）
    String params,

    // 是否使用知识库增强
    boolean useKnowledgeBase,

    // 大模型温度参数
    Double temperature
) {
}
