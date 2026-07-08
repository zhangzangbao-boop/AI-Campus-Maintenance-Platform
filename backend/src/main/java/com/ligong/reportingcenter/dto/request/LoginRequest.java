package com.ligong.reportingcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "用户ID不能为空")
    @Size(max = 50, message = "用户ID长度不能超过50")
    String userId,

    @NotBlank(message = "密码不能为空")
    String password
) {
}

