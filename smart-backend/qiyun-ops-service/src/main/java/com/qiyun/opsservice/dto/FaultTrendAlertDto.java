package com.qiyun.opsservice.dto;

import com.qiyun.opsservice.domain.enums.FaultTrendRiskLevel;
import java.time.LocalDateTime;

public record FaultTrendAlertDto(
    Long id,
    String location,
    String category,
    Integer periodDays,
    Long ticketCount,
    Long previousCount,
    Double growthRate,
    FaultTrendRiskLevel riskLevel,
    String aiReason,
    String suggestion,
    LocalDateTime lastDetectedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
