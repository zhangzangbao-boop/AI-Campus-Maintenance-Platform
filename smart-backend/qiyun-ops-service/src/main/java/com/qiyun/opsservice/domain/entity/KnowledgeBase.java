package com.qiyun.opsservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 知识库实体
 * 对应表: repair_knowledge_base
 */
@Getter
@Setter
@Entity
@Table(name = "repair_knowledge_base")
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long knowledgeId;

    @Column(name = "category_key", length = 50)
    private String categoryKey;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "symptom_keywords", columnDefinition = "TEXT")
    private String symptomKeywords;

    @Column(name = "solution_steps", columnDefinition = "TEXT")
    private String solutionSteps;

    @Column(name = "safety_notes", columnDefinition = "TEXT")
    private String safetyNotes;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

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
        if (enabled == null) {
            enabled = true;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}