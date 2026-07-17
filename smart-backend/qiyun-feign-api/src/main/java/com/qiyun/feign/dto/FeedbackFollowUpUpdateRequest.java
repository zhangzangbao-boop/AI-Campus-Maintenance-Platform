package com.qiyun.feign.dto;

public record FeedbackFollowUpUpdateRequest(
    String operatorId,
    String status,
    String note
) {
}
