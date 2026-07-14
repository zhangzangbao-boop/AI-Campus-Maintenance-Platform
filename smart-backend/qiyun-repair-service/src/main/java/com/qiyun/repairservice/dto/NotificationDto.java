package com.qiyun.repairservice.dto;

import java.time.LocalDateTime;

public record NotificationDto(
    Long notificationId,
    String receiverId,
    String title,
    String content,
    Long relatedOrderId,
    boolean read,
    LocalDateTime createdAt
) {
}