package com.ligong.reportingcenter.dto;

import com.ligong.reportingcenter.domain.enums.RepairProcessActionType;
import java.time.LocalDateTime;

public record RepairProcessRecordDto(
    Long id,
    Long ticketId,
    String staffId,
    String staffName,
    RepairProcessActionType actionType,
    String content,
    String imageUrl,
    LocalDateTime createdAt
) {
}
