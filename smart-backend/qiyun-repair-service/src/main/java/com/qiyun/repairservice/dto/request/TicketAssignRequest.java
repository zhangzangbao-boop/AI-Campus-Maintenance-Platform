package com.qiyun.repairservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketAssignRequest(
    @NotBlank(message = "操作人ID不能为空")
    @Size(max = 50, message = "操作人ID长度不能超过50")
    String operatorId,

    @NotBlank(message = "维修工ID不能为空")
    @Size(max = 50, message = "维修工ID长度不能超过50")
    String staffId
) {
}