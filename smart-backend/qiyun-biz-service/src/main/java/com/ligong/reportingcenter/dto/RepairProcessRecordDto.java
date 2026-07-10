package com.ligong.reportingcenter.dto;

import com.ligong.reportingcenter.domain.enums.RepairProcessActionType;
import java.time.LocalDateTime;

/**
 * 维修过程记录 DTO
 */
public record RepairProcessRecordDto(
    Long id,
    Long ticketId,
    String staffId,
    String staffName,
    RepairProcessActionType actionType,
    String actionTypeDescription,
    String content,
    String imageUrl,

    // 新增字段
    LocalDateTime arrivedAt,
    String repairDescription,
    String materialsUsed,
    LocalDateTime finishedAt,
    Integer durationMinutes,
    String remarks,

    LocalDateTime createdAt
) {
}
