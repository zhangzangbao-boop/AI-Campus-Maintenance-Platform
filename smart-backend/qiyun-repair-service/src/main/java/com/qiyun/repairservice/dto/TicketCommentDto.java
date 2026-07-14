package com.qiyun.repairservice.dto;

import com.qiyun.repairservice.domain.enums.TicketCommentType;
import com.qiyun.repairservice.domain.enums.UserRole;
import java.time.LocalDateTime;

public record TicketCommentDto(
    Long id,
    Long ticketId,
    String authorId,
    String authorName,
    UserRole authorRole,
    TicketCommentType commentType,
    String content,
    String imageUrl,
    LocalDateTime createdAt
) {
}