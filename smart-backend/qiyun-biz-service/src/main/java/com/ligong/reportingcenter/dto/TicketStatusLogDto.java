package com.ligong.reportingcenter.dto;

import com.ligong.reportingcenter.domain.enums.TicketStatus;
import java.time.LocalDateTime;

public record TicketStatusLogDto(
    Long logId,
    TicketStatus oldStatus,
    TicketStatus newStatus,
    String operatorId,
    LocalDateTime logTime
) {
}

