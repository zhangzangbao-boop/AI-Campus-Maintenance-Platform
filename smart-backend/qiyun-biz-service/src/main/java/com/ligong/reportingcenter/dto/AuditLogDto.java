package com.ligong.reportingcenter.dto;

import java.time.LocalDateTime;

public record AuditLogDto(
    Long auditId,
    String actorId,
    String actorName,
    String action,
    String module,
    String targetType,
    String targetId,
    String detail,
    String requestMethod,
    String requestPath,
    Boolean success,
    String ipAddress,
    LocalDateTime createdAt
) {
}
