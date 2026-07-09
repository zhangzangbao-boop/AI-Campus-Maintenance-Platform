package com.ligong.reportingcenter.dto;

import com.ligong.reportingcenter.domain.enums.UserRole;

public record UserDto(
    String userId,
    String nickname,
    String contactPhone,
    UserRole role,
    boolean active
) {
}

