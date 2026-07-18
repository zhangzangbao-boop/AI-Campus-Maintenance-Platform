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
@Table(name = "ai_ticket_analysis_correction")
public class AiTicketAnalysisCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long correctionId;

    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "previous_category_key", length = 50)
    private String previousCategoryKey;

    @Column(name = "previous_urgency", length = 20)
    private String previousUrgency;

    @Column(name = "previous_suggestion", columnDefinition = "TEXT")
    private String previousSuggestion;

    @Column(name = "new_category_key", length = 50)
    private String newCategoryKey;

    @Column(name = "new_urgency", length = 20)
    private String newUrgency;

    @Column(name = "new_suggestion", columnDefinition = "TEXT")
    private String newSuggestion;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(name = "corrected_by", length = 255, nullable = false)
    private String correctedBy;

    @Column(name = "corrected_at", nullable = false)
    private LocalDateTime correctedAt;

    @PrePersist
    public void onCreate() {
        if (correctedAt == null) {
            correctedAt = LocalDateTime.now();
        }
    }
}
