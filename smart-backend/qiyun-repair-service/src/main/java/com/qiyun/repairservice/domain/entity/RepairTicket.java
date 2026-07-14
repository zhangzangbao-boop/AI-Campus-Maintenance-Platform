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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "repair_order")
public class RepairTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long ticketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_number", referencedColumnName = "user_number")
    private UserReference student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_key", referencedColumnName = "category_key")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repairman_id", referencedColumnName = "user_number")
    private UserReference staff;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TicketStatus status;

    @Column(name = "location", nullable = false, length = 100)
    private String locationText;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "priority", length = 10)
    private String priority;

    @Column(name = "repair_notes", columnDefinition = "TEXT")
    private String repairNotes;

    @Column(name = "process_notes", columnDefinition = "TEXT")
    private String processNotes;

    @Column(name = "estimated_completion_time")
    private LocalDateTime estimatedCompletionTime;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "ticket", fetch = FetchType.LAZY)
    private List<TicketImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", fetch = FetchType.LAZY)
    private List<TicketStatusLog> statusLogs = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = TicketStatus.WAITING_ACCEPT;
        }
        if (priority == null) {
            priority = "medium";
        }
    }
}