package com.ligong.reportingcenter.config;

import com.ligong.reportingcenter.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupService backupService;

    @Value("${backup.auto-enabled:false}")
    private boolean autoEnabled;

    @Scheduled(cron = "${backup.cron:0 30 2 * * ?}")
    public void scheduledBackup() {
        if (!autoEnabled) {
            return;
        }
        try {
            log.info("开始执行定时备份任务...");
            backupService.performBackup();
            log.info("定时备份任务执行成功");
        } catch (Exception e) {
            log.error("定时备份任务执行失败", e);
        }
    }
}


