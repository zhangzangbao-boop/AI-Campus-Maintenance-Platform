package com.ligong.reportingcenter.dto.request;

import com.ligong.reportingcenter.domain.enums.RepairProcessActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RepairProcessRecordRequest(
    @NotNull(message = "请选择维修过程类型")
    RepairProcessActionType actionType,

    @NotBlank(message = "维修过程内容不能为空")
    @Size(max = 1000, message = "维修过程内容不能超过1000个字符")
    String content,

    String imageUrl
) {
}
