package com.qiyun.userservice.dto.response;

import com.qiyun.userservice.dto.UserDto;

public record AuthResponse(
    UserDto user,
    String token
) {
    public AuthResponse(UserDto user) {
        this(user, null);
    }
}