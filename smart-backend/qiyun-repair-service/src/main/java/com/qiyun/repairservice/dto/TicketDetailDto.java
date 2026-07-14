package com.qiyun.repairservice.dto;

import com.qiyun.repairservice.domain.enums.TicketStatus;
import java.time.LocalDateTime;
import java.util.List;

public record TicketDetailDto(
    Long ticketId,
    TicketStatus status,
    String categoryName,
    String studentId,
    String studentName,
    String staffId,
    String staffName,
    String locationText,
    String description,
    String rejectionReason,
    String priority,
    String repairNotes,
    String processNotes,
    LocalDateTime estimatedCompletionTime,
    LocalDateTime createdAt,
    LocalDateTime assignedAt,
    LocalDateTime completedAt,
    LocalDateTime closedAt,
    List<TicketImageDto> images,
    List<TicketStatusLogDto> logs,
    RatingDto rating
) {
}