package com.qiyun.opsservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SystemConfigRequest(
    @NotBlank(message = "配置值不能为空")
    String configValue,

    String description
) {}