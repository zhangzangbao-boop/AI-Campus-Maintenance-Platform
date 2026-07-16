package com.qiyun.repairservice.config;

import com.qiyun.repairservice.service.SlaAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SLA告警定时任务调度器
 * 定期检查即将超时和已超时的工单，发送告警通知
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaScheduler {

    private final SlaAlertService slaAlertService;

    /**
     * 是否启用SLA告警定时任务
     */
    @Value("${sla.alert.enabled:false}")
    private boolean alertEnabled;

    /**
     * SLA告警检查间隔（分钟）
     */
    @Value("${sla.alert.interval-minutes:5}")
    private int intervalMinutes;

    /**
     * 定时检查SLA告警
     * 默认每5分钟执行一次，可通过配置调整
     */
    @Scheduled(fixedDelayString = "${sla.alert.interval-ms:300000}")
    public void checkSlaAlerts() {
        if (!alertEnabled) {
            log.debug("SLA告警定时任务已禁用");
            return;
        }

        try {
            log.info("开始执行SLA告警检查...");
            var result = slaAlertService.checkAndAlert();
            log.info("SLA告警检查完成: 警告={}, 超时={}, 通知失败={}, 优先级升级={}",
                result.get("warningCount"),
                result.get("overdueCount"),
                result.get("notificationFailedCount"),
                result.get("priorityUpgradedCount"));
        } catch (Exception e) {
            log.error("SLA告警定时任务执行失败", e);
        }
    }
}