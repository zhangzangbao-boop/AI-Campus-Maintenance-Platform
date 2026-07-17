package com.qiyun.repairservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "repair_feedback")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long ratingId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repair_order_id", nullable = false, unique = true)
    private RepairTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_number", referencedColumnName = "user_number", nullable = false)
    private UserReference student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repairman_id", referencedColumnName = "user_number", nullable = false)
    private UserReference staff;

    @Min(1)
    @Max(5)
    @Column(name = "rating", nullable = false)
    private Integer score;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Min(1)
    @Max(5)
    @Column(name = "speed_rating")
    private Integer speedRating;

    @Min(1)
    @Max(5)
    @Column(name = "quality_rating")
    private Integer qualityRating;

    @Min(1)
    @Max(5)
    @Column(name = "attitude_rating")
    private Integer attitudeRating;

    @Column(name = "resolved")
    private Boolean resolved;

    @Column(name = "anonymous")
    private Boolean anonymous;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime ratedAt;

    @Column(name = "sentiment", length = 20)
    private String sentiment;

    @Column(name = "sentiment_score")
    private Double sentimentScore;

    @Column(name = "sentiment_keywords", columnDefinition = "TEXT")
    private String sentimentKeywords;

    @Column(name = "sentiment_summary", columnDefinition = "TEXT")
    private String sentimentSummary;

    @Column(name = "sentiment_analyzed_at")
    private LocalDateTime sentimentAnalyzedAt;

    @PrePersist
    public void onCreate() {
        if (ratedAt == null) {
            ratedAt = LocalDateTime.now();
        }
    }
}
