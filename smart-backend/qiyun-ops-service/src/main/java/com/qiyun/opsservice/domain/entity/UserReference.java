package com.qiyun.opsservice.domain.entity;

import com.qiyun.opsservice.domain.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户引用实体
 * 仅包含运维业务所需的用户字段，不包含密码等敏感信息
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user")
public class UserReference {

    @Id
    @Column(name = "user_number")
    private String userId;

    @Column(name = "name", nullable = false, length = 100)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "enabled")
    private Boolean isActive = true;

    @Column(name = "phone", length = 20)
    private String contactPhone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
    }
}