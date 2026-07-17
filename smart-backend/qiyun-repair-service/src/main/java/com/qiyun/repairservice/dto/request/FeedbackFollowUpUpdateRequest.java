package com.qiyun.repairservice.dto.request;

import com.qiyun.repairservice.domain.enums.FeedbackFollowUpStatus;

public record FeedbackFollowUpUpdateRequest(
    String operatorId,
    FeedbackFollowUpStatus status,
    String note
) {
}
