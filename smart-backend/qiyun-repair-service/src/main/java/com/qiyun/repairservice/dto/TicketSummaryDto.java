package com.qiyun.repairservice.dto;

import com.qiyun.repairservice.domain.enums.TicketStatus;
import java.time.LocalDateTime;

public record TicketSummaryDto(
    Long ticketId,
    TicketStatus status,
    String categoryName,
    String studentId,
    String staffId,
    String locationText,
    String description,
    String priority,
    LocalDateTime createdAt,
    LocalDateTime assignedAt,
    LocalDateTime estimatedCompletionTime,
    Integer ratingScore,
    Boolean deleted,
    LocalDateTime deletedAt
) {
}