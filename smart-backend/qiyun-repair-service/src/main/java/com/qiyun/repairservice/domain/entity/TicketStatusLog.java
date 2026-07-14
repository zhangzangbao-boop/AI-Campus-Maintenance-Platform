package com.qiyun.repairservice.domain.entity;

import com.qiyun.repairservice.domain.enums.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "repair_order_status_log")
public class TicketStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repair_order_id", nullable = false)
    private RepairTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operate_user_id", referencedColumnName = "user_number")
    private UserReference changedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_status", length = 30)
    private TicketStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_status", nullable = false, length = 30)
    private TicketStatus newStatus;

    @Column(name = "operate_time", nullable = false)
    private LocalDateTime logTime;

    @PrePersist
    public void onCreate() {
        if (logTime == null) {
            logTime = LocalDateTime.now();
        }
    }
}