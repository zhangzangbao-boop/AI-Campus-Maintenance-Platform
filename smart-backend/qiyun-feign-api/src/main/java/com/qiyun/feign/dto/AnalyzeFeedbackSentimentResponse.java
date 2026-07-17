package com.qiyun.feign.dto;

import java.util.List;

public record AnalyzeFeedbackSentimentResponse(
    String sentiment,
    Double score,
    List<String> keywords,
    String summary
) {
}
