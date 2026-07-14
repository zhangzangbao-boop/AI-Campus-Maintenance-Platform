package com.qiyun.repairservice.dto.request;

import com.qiyun.repairservice.domain.enums.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketStatusUpdateRequest(
    @NotBlank(message = "操作人ID不能为空")
    @Size(max = 50, message = "操作人ID长度不能超过50")
    String operatorId,

    @NotNull(message = "新状态不能为空")
    TicketStatus newStatus,

    @Size(max = 500, message = "驳回理由过长")
    String rejectionReason
) {
}