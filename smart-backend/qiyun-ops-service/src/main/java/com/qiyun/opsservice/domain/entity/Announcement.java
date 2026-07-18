package com.qiyun.opsservice.domain.entity;

import com.qiyun.opsservice.domain.enums.AnnouncementPriority;
import com.qiyun.opsservice.domain.enums.AnnouncementStatus;
import com.qiyun.opsservice.domain.enums.AnnouncementType;
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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "campus_announcement")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long announcementId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private AnnouncementType type = AnnouncementType.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private AnnouncementPriority priority = AnnouncementPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnnouncementStatus status = AnnouncementStatus.DRAFT;

    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @Column(name = "pinned", nullable = false)
    private Boolean pinned = false;

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
        if (type == null) {
            type = AnnouncementType.GENERAL;
        }
        if (priority == null) {
            priority = AnnouncementPriority.NORMAL;
        }
        if (status == null) {
            status = AnnouncementStatus.DRAFT;
        }
        if (pinned == null) {
            pinned = false;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
