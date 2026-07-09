package com.ligong.reportingcenter.dto;

import java.time.LocalDateTime;

public record TicketImageDto(
    Long imageId,
    String imageUrl,
    LocalDateTime uploadedAt
) {
}

