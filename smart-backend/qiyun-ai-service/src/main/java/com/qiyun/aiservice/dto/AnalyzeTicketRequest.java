package com.qiyun.aiservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * AI 分析工单请求 DTO
 */
public record AnalyzeTicketRequest(
    @NotBlank(message = "问题描述不能为空")
    String description,

    String location
) {}