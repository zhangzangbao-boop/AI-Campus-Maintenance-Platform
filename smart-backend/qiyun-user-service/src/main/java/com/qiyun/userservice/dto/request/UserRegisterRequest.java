package com.qiyun.userservice.dto.request;

import com.qiyun.userservice.domain.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRegisterRequest(
    @NotBlank(message = "用户ID不能为空")
    @Size(max = 50, message = "用户ID长度不能超过50")
    String userId,

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度需在6-100之间")
    String password,

    @NotBlank(message = "昵称不能为空")
    @Size(max = 100, message = "昵称长度不能超过100")
    String nickname,

    @Pattern(regexp = "^$|^\\d{5,20}$", message = "联系电话应为5-20位数字")
    String contactPhone,

    @NotNull(message = "角色不能为空")
    UserRole role
) {
}