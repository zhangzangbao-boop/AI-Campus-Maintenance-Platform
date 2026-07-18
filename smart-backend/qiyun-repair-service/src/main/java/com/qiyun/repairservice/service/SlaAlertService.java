package com.qiyun.repairservice.service;

import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.SlaAlert;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.repository.SlaAlertRepository;
import com.qiyun.repairservice.repository.TicketRepository;
import com.qiyun.repairservice.repository.UserReferenceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SLA告警服务
 * 处理工单SLA超时告警、优先级升级和通知推送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaAlertService {

    private final TicketRepository ticketRepository;
    private final SlaAlertRepository slaAlertRepository;
    private final UserReferenceRepository userReferenceRepository;
    private final NotificationPushService notificationPushService;
    private final RepairRuleConfigService repairRuleConfigService;

    /**
     * 执行SLA检查，处理即将超时和已超时的工单
     *
     * @return 处理结果统计
     */
    @Transactional
    public Map<String, Integer> checkAndAlert() {
        log.debug("开始执行SLA检查...");
        LocalDateTime now = LocalDateTime.now();

        int warningCount = 0;
        int overdueCount = 0;
        int notificationFailedCount = 0;
        int priorityUpgradedCount = 0;

        // 查询所有未完成、未删除的工单
        List<RepairTicket> activeTickets = ticketRepository.findAll().stream()
            .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
            .filter(t -> t.getStatus() == TicketStatus.WAITING_ACCEPT || t.getStatus() == TicketStatus.IN_PROGRESS)
            .toList();

        log.debug("找到 {} 个活跃工单需要检查SLA", activeTickets.size());

        for (RepairTicket ticket : activeTickets) {
            try {
                SlaCheckResult result = checkTicketSla(ticket, now);

                if (result == null || result.status == SlaStatus.NORMAL) {
                    continue;
                }

                // 检查是否已经发送过该级别的告警（去重）
                if (slaAlertRepository.existsByTicketIdAndAlertLevel(ticket.getTicketId(), result.status.name())) {
                    log.debug("工单 {} 已经发送过 {} 级别告警，跳过", ticket.getTicketId(), result.status);
                    continue;
                }

                // 创建告警记录
                SlaAlert alert = createAlert(ticket, result, now);
                slaAlertRepository.save(alert);

                // 发送通知给管理员
                boolean notificationSent = sendNotification(ticket, result);
                if (!notificationSent) {
                    notificationFailedCount++;
                }

                // 已超时工单：尝试升级优先级
                if (result.status == SlaStatus.OVERDUE) {
                    boolean upgraded = upgradePriority(ticket);
                    if (upgraded) {
                        priorityUpgradedCount++;
                    }
                }

                if (result.status == SlaStatus.WARNING) {
                    warningCount++;
                } else {
                    overdueCount++;
                }

                log.info("SLA告警: 工单{}, 级别={}, 类型={}, 通知={}",
                    ticket.getTicketId(), result.status, result.slaType, notificationSent ? "成功" : "失败");

            } catch (Exception e) {
                log.error("处理工单 {} SLA告警失败: {}", ticket.getTicketId(), e.getMessage(), e);
                // 单个工单失败不影响其他工单
            }
        }

        log.info("SLA检查完成: 警告={}, 超时={}, 通知失败={}, 优先级升级={}",
            warningCount, overdueCount, notificationFailedCount, priorityUpgradedCount);

        return Map.of(
            "warningCount", warningCount,
            "overdueCount", overdueCount,
            "notificationFailedCount", notificationFailedCount,
            "priorityUpgradedCount", priorityUpgradedCount
        );
    }

    /**
     * 检查工单的SLA状态
     */
    private SlaCheckResult checkTicketSla(RepairTicket ticket, LocalDateTime now) {
        String priority = normalizePriority(ticket.getPriority());
        RepairRuleConfigService.SlaRules rules = repairRuleConfigService.slaRules();
        RepairRuleConfigService.SlaPriorityRule priorityRule = rules.priority(priority);
        long responseHours = priorityRule.responseHours();
        long completionHours = priorityRule.completionHours();

        LocalDateTime startAt;
        LocalDateTime dueAt;
        String slaType;
        long limitHours;

        if (ticket.getStatus() == TicketStatus.WAITING_ACCEPT) {
            startAt = ticket.getCreatedAt();
            dueAt = startAt != null ? startAt.plusHours(responseHours) : null;
            slaType = "ACCEPTANCE";
            limitHours = responseHours;
        } else if (ticket.getStatus() == TicketStatus.IN_PROGRESS) {
            startAt = ticket.getAssignedAt() != null ? ticket.getAssignedAt() : ticket.getCreatedAt();
            dueAt = startAt != null ? startAt.plusHours(completionHours) : null;
            slaType = "COMPLETION";
            limitHours = completionHours;
        } else {
            return null;
        }

        if (dueAt == null) {
            return null;
        }

        boolean overdue = now.isAfter(dueAt);
        long warningThreshold = Math.max(1, Math.round(limitHours * rules.warningRatio()));
        boolean warning = !overdue && !now.isBefore(dueAt.minusHours(warningThreshold));

        if (!overdue && !warning) {
            return new SlaCheckResult(SlaStatus.NORMAL, slaType, dueAt, 0L);
        }

        long remainingHours = java.time.Duration.between(now, dueAt).toHours();
        long overdueHours = overdue ? java.time.Duration.between(dueAt, now).toHours() : 0;

        SlaStatus status = overdue ? SlaStatus.OVERDUE : SlaStatus.WARNING;
        return new SlaCheckResult(status, slaType, dueAt, overdue ? -overdueHours : remainingHours);
    }

    /**
     * 创建告警记录
     */
    private SlaAlert createAlert(RepairTicket ticket, SlaCheckResult result, LocalDateTime now) {
        SlaAlert alert = new SlaAlert();
        alert.setTicketId(ticket.getTicketId());
        alert.setAlertLevel(result.status.name());
        alert.setSlaType(result.slaType);
        alert.setTicketPriority(ticket.getPriority());
        alert.setDueAt(result.dueAt);
        alert.setRemainingHours(result.remainingHours);
        alert.setCreatedAt(now);
        return alert;
    }

    /**
     * 发送通知给管理员
     */
    private boolean sendNotification(RepairTicket ticket, SlaCheckResult result) {
        try {
            String title;
            String content;

            if (result.status == SlaStatus.OVERDUE) {
                title = "工单超时告警";
                content = String.format(
                    "工单 #%d 已超时！\n" +
                    "位置：%s\n" +
                    "分类：%s\n" +
                    "超时时长：%d 小时\n" +
                    "请立即处理！",
                    ticket.getTicketId(),
                    ticket.getLocationText(),
                    ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : "未知",
                    Math.abs(result.remainingHours)
                );
            } else {
                title = "工单即将超时提醒";
                content = String.format(
                    "工单 #%d 即将超时！\n" +
                    "位置：%s\n" +
                    "分类：%s\n" +
                    "剩余时间：%d 小时\n" +
                    "请尽快处理！",
                    ticket.getTicketId(),
                    ticket.getLocationText(),
                    ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : "未知",
                    result.remainingHours
                );
            }

            // 获取所有管理员
            List<UserReference> admins = userReferenceRepository.findByRoleAndIsActiveTrue(UserRole.ADMIN);
            if (admins.isEmpty()) {
                log.warn("没有活跃的管理员用户，无法发送SLA告警通知");
                return false;
            }

            // 批量发送通知（包含实时推送）
            notificationPushService.notifyAndPushBatch(admins, title, content, ticket);
            return true;

        } catch (Exception e) {
            log.error("发送SLA告警通知失败: ticketId={}, error={}", ticket.getTicketId(), e.getMessage());
            return false;
        }
    }

    /**
     * 升级工单优先级（仅对已超时工单）
     * 规则：low → medium → high，已是high不再升级
     */
    @Transactional
    public boolean upgradePriority(RepairTicket ticket) {
        String currentPriority = normalizePriority(ticket.getPriority());

        // 已是最高优先级，不再升级
        if ("high".equals(currentPriority)) {
            log.debug("工单 {} 已是高优先级，无需升级", ticket.getTicketId());
            return false;
        }

        String newPriority = switch (currentPriority) {
            case "low" -> "medium";
            case "medium" -> "high";
            default -> "medium";
        };

        ticket.setPriority(newPriority);
        ticketRepository.save(ticket);

        log.info("工单 {} 优先级升级: {} → {}", ticket.getTicketId(), currentPriority, newPriority);
        return true;
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "medium";
        }
        String normalized = priority.toLowerCase(java.util.Locale.ROOT);
        if ("high".equals(normalized) || "medium".equals(normalized) || "low".equals(normalized)) {
            return normalized;
        }
        return "medium";
    }

    /**
     * SLA状态枚举
     */
    private enum SlaStatus {
        NORMAL,    // 正常
        WARNING,   // 即将超时（剩余时间小于25%）
        OVERDUE    // 已超时
    }

    /**
     * SLA检查结果
     */
    private record SlaCheckResult(SlaStatus status, String slaType, LocalDateTime dueAt, long remainingHours) {}
}
