package com.qiyun.repairservice.domain.enums;

/**
 * 维修过程操作类型枚举
 */
public enum RepairProcessActionType {
    /** 到场确认 */
    ARRIVED("到场确认"),

    /** 诊断中 */
    DIAGNOSING("诊断中"),

    /** 维修中 */
    REPAIRING("维修中"),

    /** 使用材料 */
    MATERIAL_USED("使用材料"),

    /** 暂停/等待 */
    PAUSED("暂停/等待"),

    /** 完成 */
    FINISHED("完成"),

    /** 转派申请 */
    TRANSFER_REQUEST("转派申请"),

    /** 转派批准 */
    TRANSFER_APPROVED("转派批准"),

    /** 转派拒绝 */
    TRANSFER_REJECTED("转派拒绝");

    private final String description;

    RepairProcessActionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}