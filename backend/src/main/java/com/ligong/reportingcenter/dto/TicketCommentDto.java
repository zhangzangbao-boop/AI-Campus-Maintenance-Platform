package com.ligong.reportingcenter.dto;

import com.ligong.reportingcenter.domain.enums.TicketCommentType;
import com.ligong.reportingcenter.domain.enums.UserRole;
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
