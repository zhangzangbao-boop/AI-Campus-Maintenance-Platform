package com.qiyun.repairservice.dto;

import java.time.LocalDateTime;

public record CompletionSummaryDto(
    Long ticketId,
    String summary,
    String source,
    String provider,
    String model,
    LocalDateTime generatedAt
) {
}
