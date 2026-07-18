package com.qiyun.opsservice.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FaultTrendDashboardDto(
    List<FaultTrendItemDto> trends,
    List<FaultTrendAlertDto> alerts,
    LocalDateTime generatedAt,
    Integer sevenDayThreshold,
    Integer thirtyDayThreshold
) {}
