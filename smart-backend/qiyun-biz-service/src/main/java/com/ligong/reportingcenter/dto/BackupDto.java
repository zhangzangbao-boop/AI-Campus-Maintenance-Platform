package com.ligong.reportingcenter.dto;

import java.time.LocalDateTime;

public record BackupDto(
    String fileName,
    String filePath,
    long fileSize,
    LocalDateTime backupTime,
    String status
) {
}


