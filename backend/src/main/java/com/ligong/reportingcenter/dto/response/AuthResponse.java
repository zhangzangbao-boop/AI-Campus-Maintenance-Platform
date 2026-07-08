package com.ligong.reportingcenter.dto.response;

import com.ligong.reportingcenter.dto.UserDto;

public record AuthResponse(
    UserDto user,
    String token
) {
    public AuthResponse(UserDto user) {
        this(user, null);
    }
}