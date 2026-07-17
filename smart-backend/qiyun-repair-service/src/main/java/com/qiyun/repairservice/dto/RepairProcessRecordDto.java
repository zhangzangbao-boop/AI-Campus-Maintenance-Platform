package com.qiyun.repairservice.dto;

import com.qiyun.repairservice.domain.enums.RepairProcessActionType;
import java.time.LocalDateTime;
import java.util.List;

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
    List<String> imageUrls,

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