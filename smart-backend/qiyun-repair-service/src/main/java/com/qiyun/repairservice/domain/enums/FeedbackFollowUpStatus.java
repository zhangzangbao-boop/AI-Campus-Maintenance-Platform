package com.qiyun.repairservice.domain.enums;

public enum FeedbackFollowUpStatus {
    NO_NEED,
    PENDING,
    HANDLED,

    /**
     * Legacy value kept so older rows can still be loaded and normalized.
     */
    PROCESSING,
    RESOLVED
}
