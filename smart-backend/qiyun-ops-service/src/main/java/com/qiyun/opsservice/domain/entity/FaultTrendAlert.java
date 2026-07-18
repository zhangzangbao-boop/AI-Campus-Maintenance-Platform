package com.qiyun.opsservice.domain.entity;

import com.qiyun.opsservice.domain.enums.FaultTrendRiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "fault_trend_alert",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_fault_trend_alert_scope", columnNames = {"location", "category_key", "period_days"})
    }
)
public class FaultTrendAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long alertId;

    @Column(name = "location", nullable = false, length = 200)
    private String location;

    @Column(name = "category_key", nullable = false, length = 80)
    private String category;

    @Column(name = "period_days", nullable = false)
    private Integer periodDays;

    @Column(name = "ticket_count", nullable = false)
    private Long ticketCount;

    @Column(name = "previous_count", nullable = false)
    private Long previousCount;

    @Column(name = "growth_rate", nullable = false)
    private Double growthRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private FaultTrendRiskLevel riskLevel;

    @Column(name = "ai_reason", columnDefinition = "TEXT")
    private String aiReason;

    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "last_detected_at", nullable = false)
    private LocalDateTime lastDetectedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (lastDetectedAt == null) {
            lastDetectedAt = now;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
