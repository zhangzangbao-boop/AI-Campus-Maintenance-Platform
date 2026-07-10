package com.ligong.reportingcenter.domain.entity;

import com.ligong.reportingcenter.domain.enums.RepairProcessActionType;
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

/**
 * 维修过程记录实体
 *
 * 记录维修工在处理工单过程中的各个关键节点信息：
 * - 到场确认
 * - 维修描述
 * - 使用材料
 * - 完成确认
 */
@Getter
@Setter
@Entity
@Table(name = "repair_process_record")
public class RepairProcessRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long recordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repair_order_id", nullable = false)
    private RepairTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", referencedColumnName = "user_number", nullable = false)
    private User staff;

    /**
     * 操作类型：到场、维修中、完成等
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private RepairProcessActionType actionType;

    /**
     * 操作内容/描述
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 图片URL
     */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // ==================== 新增字段 ====================

    /**
     * 到场时间（维修工到达现场的时间）
     */
    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    /**
     * 维修描述（详细的维修过程说明）
     */
    @Column(name = "repair_description", columnDefinition = "TEXT")
    private String repairDescription;

    /**
     * 使用材料（维修过程中使用的材料和配件，JSON格式）
     */
    @Column(name = "materials_used", columnDefinition = "TEXT")
    private String materialsUsed;

    /**
     * 完成时间（该步骤完成的时间）
     */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * 耗时（分钟）- 该步骤花费的时间
     */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /**
     * 备注
     */
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    /**
     * 创建时间（记录创建时间）
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        // 如果是到场类型，自动设置到场时间
        if (actionType == RepairProcessActionType.ARRIVED && arrivedAt == null) {
            arrivedAt = LocalDateTime.now();
        }
        // 如果是完成类型，自动设置完成时间
        if (actionType == RepairProcessActionType.FINISHED && finishedAt == null) {
            finishedAt = LocalDateTime.now();
        }
    }
}
