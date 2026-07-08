package com.ligong.reportingcenter.dto;

import com.ligong.reportingcenter.domain.enums.TicketStatus;
import java.time.LocalDateTime;

public record SimilarTicketDto(
    Long ticketId,
    String categoryName,
    String locationText,
    String description,
    TicketStatus status,
    String staffId,
    String repairNotes,
    String processNotes,
    Integer ratingScore,
    double similarity,
    LocalDateTime createdAt
) {
}
