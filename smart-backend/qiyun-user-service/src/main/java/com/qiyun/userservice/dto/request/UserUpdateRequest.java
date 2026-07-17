package com.qiyun.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过50")
    private String nickname;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String contactPhone;

    // 头像URL允许为空，不再强制必填
    @Pattern(regexp = "^$|^https?://.*", message = "头像URL格式不正确")
    private String avatarUrl;

    @Size(max = 500, message = "负责区域长度不能超过500")
    private String responsibleArea;

    @Size(max = 500, message = "专业特长长度不能超过500")
    private String specialties;
}
