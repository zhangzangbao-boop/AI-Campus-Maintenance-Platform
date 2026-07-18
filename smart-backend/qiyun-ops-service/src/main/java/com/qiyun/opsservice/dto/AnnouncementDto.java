package com.qiyun.opsservice.dto;

import com.qiyun.opsservice.domain.enums.AnnouncementPriority;
import com.qiyun.opsservice.domain.enums.AnnouncementStatus;
import com.qiyun.opsservice.domain.enums.AnnouncementType;
import java.time.LocalDateTime;

public record AnnouncementDto(
    Long id,
    String title,
    String content,
    AnnouncementType type,
    AnnouncementPriority priority,
    AnnouncementStatus status,
    LocalDateTime publishTime,
    LocalDateTime expireTime,
    Boolean pinned,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
