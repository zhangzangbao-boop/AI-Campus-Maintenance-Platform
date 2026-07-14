package com.qiyun.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "原密码不能为空")
    String oldPassword,

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度应为 6-64 位")
    String newPassword
) {
}