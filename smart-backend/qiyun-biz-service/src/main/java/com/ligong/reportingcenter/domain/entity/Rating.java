package com.ligong.reportingcenter.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
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
@Table(name = "repair_feedback") // 修改为与数据库一致
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") // 修改为主键列名与数据库一致
    private Long ratingId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repair_order_id", nullable = false, unique = true) // 修改外键列名与数据库一致
    private RepairTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_number", referencedColumnName = "user_number", nullable = false) // 添加 referencedColumnName 确保正确关联
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repairman_id", referencedColumnName = "user_number", nullable = false) // 添加 referencedColumnName 确保正确关联
    private User staff;

    @Min(1)
    @Max(5)
    @Column(name = "rating", nullable = false) // 修改列名
    private Integer score;

    @Column(name = "comment", columnDefinition = "TEXT") // 保持列名
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

    @Column(name = "created_at", nullable = false) // 修改列名
    private LocalDateTime ratedAt;

    @PrePersist
    public void onCreate() {
        if (ratedAt == null) {
            ratedAt = LocalDateTime.now();
        }
    }
}
