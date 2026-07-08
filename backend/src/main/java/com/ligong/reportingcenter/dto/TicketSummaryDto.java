package com.ligong.reportingcenter.dto;

import com.ligong.reportingcenter.domain.enums.TicketStatus;
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