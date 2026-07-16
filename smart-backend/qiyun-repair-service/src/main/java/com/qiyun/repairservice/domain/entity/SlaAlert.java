package com.qiyun.repairservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * SLA告警记录
 * 用于去重：同一工单同一告警级别只发送一次告警
 */
@Getter
@Setter
@Entity
@Table(
    name = "sla_alert",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ticket_alert_level", columnNames = {"ticket_id", "alert_level"})
    },
    indexes = {
        @Index(name = "idx_ticket_id", columnList = "ticket_id"),
        @Index(name = "idx_alert_level", columnList = "alert_level"),
        @Index(name = "idx_created_at", columnList = "created_at")
    }
)
public class SlaAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 关联的工单ID
     */
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /**
     * 告警级别：WARNING（即将超时）或 OVERDUE（已超时）
     */
    @Column(name = "alert_level", nullable = false, length = 20)
    private String alertLevel;

    /**
     * SLA类型：ACCEPTANCE（受理时限）或 COMPLETION（完成时限）
     */
    @Column(name = "sla_type", nullable = false, length = 20)
    private String slaType;

    /**
     * 工单优先级（告警时的快照）
     */
    @Column(name = "ticket_priority", length = 10)
    private String ticketPriority;

    /**
     * 超时/即将超时的截止时间
     */
    @Column(name = "due_at")
    private LocalDateTime dueAt;

    /**
     * 剩余时间（小时，负数表示已超时）
     */
    @Column(name = "remaining_hours")
    private Long remainingHours;

    /**
     * 通知是否发送成功
     */
    @Column(name = "notification_sent", nullable = false)
    private Boolean notificationSent = false;

    /**
     * 优先级是否已升级
     */
    @Column(name = "priority_upgraded", nullable = false)
    private Boolean priorityUpgraded = false;

    /**
     * 告警时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 备注信息
     */
    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (notificationSent == null) {
            notificationSent = false;
        }
        if (priorityUpgraded == null) {
            priorityUpgraded = false;
        }
    }
}