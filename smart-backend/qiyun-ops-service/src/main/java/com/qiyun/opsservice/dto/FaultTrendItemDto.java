package com.qiyun.opsservice.dto;

import com.qiyun.opsservice.domain.enums.FaultTrendRiskLevel;

public record FaultTrendItemDto(
    String location,
    String category,
    Integer periodDays,
    Long ticketCount,
    Long previousCount,
    Double growthRate,
    FaultTrendRiskLevel riskLevel,
    Boolean alertTriggered
) {}
