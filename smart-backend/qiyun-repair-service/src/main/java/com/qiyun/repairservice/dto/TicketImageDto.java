package com.qiyun.repairservice.dto;

import java.time.LocalDateTime;

public record TicketImageDto(
    Long imageId,
    String imageUrl,
    LocalDateTime uploadedAt
) {
}