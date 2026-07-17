package com.qiyun.feign.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalyzeFeedbackSentimentRequest(
    @NotBlank String comment
) {
}
