package com.qiyun.opsservice.dto;

import java.time.LocalDateTime;

public record NotificationDto(
    Long id,
    String receiverId,
    String title,
    String content,
    Long relatedOrderId,
    Boolean readFlag,
    LocalDateTime createdAt
) {
}