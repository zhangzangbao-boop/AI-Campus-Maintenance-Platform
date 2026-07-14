package com.qiyun.repairservice.dto.request;

import com.qiyun.repairservice.domain.enums.RepairProcessActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 维修过程记录请求 DTO
 */
public record RepairProcessRecordRequest(
    /**
     * 操作类型
     */
    @NotNull(message = "请选择维修过程类型")
    RepairProcessActionType actionType,

    /**
     * 操作内容/描述
     */
    @NotBlank(message = "维修过程内容不能为空")
    @Size(max = 2000, message = "维修过程内容不能超过2000个字符")
    String content,

    /**
     * 图片URL
     */
    String imageUrl,

    // ==================== 新增字段 ====================

    /**
     * 到场时间（用于到场确认）
     */
    LocalDateTime arrivedAt,

    /**
     * 维修描述（详细的维修过程说明）
     */
    @Size(max = 2000, message = "维修描述不能超过2000个字符")
    String repairDescription,

    /**
     * 使用材料（JSON格式，如：[{"name":"滤网","quantity":1,"unit":"个"}]）
     */
    @Size(max = 2000, message = "使用材料信息不能超过2000个字符")
    String materialsUsed,

    /**
     * 完成时间（用于完成确认）
     */
    LocalDateTime finishedAt,

    /**
     * 耗时（分钟）
     */
    Integer durationMinutes,

    /**
     * 备注
     */
    @Size(max = 500, message = "备注不能超过500个字符")
    String remarks
) {
    /**
     * 简化构造方法（兼容旧接口）
     */
    public RepairProcessRecordRequest(RepairProcessActionType actionType, String content, String imageUrl) {
        this(actionType, content, imageUrl, null, null, null, null, null, null);
    }
}