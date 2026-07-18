package com.qiyun.repairservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiTicketAnalysisCorrectionRequest(
    @Size(max = 50)
    String categoryKey,

    @Size(max = 20)
    String urgency,

    @Size(max = 4000)
    String suggestion,

    @NotBlank
    @Size(max = 1000)
    String reason
) {
}
