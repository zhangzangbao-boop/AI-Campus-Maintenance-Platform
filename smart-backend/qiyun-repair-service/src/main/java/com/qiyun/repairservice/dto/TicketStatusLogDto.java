package com.qiyun.repairservice.dto;

import com.qiyun.repairservice.domain.enums.TicketStatus;
import java.time.LocalDateTime;

public record TicketStatusLogDto(
    Long logId,
    TicketStatus oldStatus,
    TicketStatus newStatus,
    String operatorId,
    LocalDateTime logTime
) {
}