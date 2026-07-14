package com.qiyun.userservice.dto;

import com.qiyun.userservice.domain.enums.UserRole;

public record UserDto(
    String userId,
    String nickname,
    String contactPhone,
    UserRole role,
    boolean active
) {
}