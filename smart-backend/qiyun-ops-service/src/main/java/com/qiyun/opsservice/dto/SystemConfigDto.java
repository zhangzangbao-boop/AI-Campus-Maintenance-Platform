package com.qiyun.opsservice.dto;

import java.time.LocalDateTime;

public record SystemConfigDto(
    String configKey,
    String configValue,
    String description,
    String updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}