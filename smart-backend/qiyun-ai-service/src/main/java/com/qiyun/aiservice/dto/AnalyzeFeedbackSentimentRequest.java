package com.qiyun.aiservice.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalyzeFeedbackSentimentRequest(
    @NotBlank String comment
) {
}
