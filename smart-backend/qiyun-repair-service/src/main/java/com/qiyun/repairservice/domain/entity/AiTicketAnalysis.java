package com.qiyun.repairservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_ticket_analysis")
public class AiTicketAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long analysisId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "category_key", length = 50)
    private String categoryKey;

    @Column(name = "location_text", length = 200)
    private String locationText;

    @Column(name = "priority", length = 20)
    private String priority;

    @Column(name = "urgency", length = 20)
    private String urgency;

    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "safety_tips", columnDefinition = "TEXT")
    private String safetyTips;

    @Column(name = "provider", length = 80)
    private String provider;

    @Column(name = "model", length = 120)
    private String model;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "final_category_key", length = 50)
    private String finalCategoryKey;

    @Column(name = "final_urgency", length = 20)
    private String finalUrgency;

    @Column(name = "final_suggestion", columnDefinition = "TEXT")
    private String finalSuggestion;

    @Column(name = "correction_reason", columnDefinition = "TEXT")
    private String correctionReason;

    @Column(name = "corrected_by", length = 255)
    private String correctedBy;

    @Column(name = "corrected_at")
    private LocalDateTime correctedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
