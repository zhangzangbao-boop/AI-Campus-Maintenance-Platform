package com.qiyun.userservice.controller;

import com.qiyun.userservice.domain.enums.UserRole;
import com.qiyun.userservice.dto.UserDto;
import com.qiyun.userservice.dto.request.LoginRequest;
import com.qiyun.userservice.dto.request.UserRegisterRequest;
import com.qiyun.userservice.dto.response.AuthResponse;
import com.qiyun.userservice.service.UserService;
import com.qiyun.userservice.util.JwtUtil;
import com.qiyun.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody UserRegisterRequest request) {
        // 公开注册只允许创建学生账号，STAFF和ADMIN必须由管理员创建
        if (request.role() != UserRole.STUDENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "公开注册仅允许创建学生账号");
        }
        return userService.register(request);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        var authResponse = userService.login(request);
        // 生成JWT Token
        String token = jwtUtil.generateToken(authResponse.user().userId(), authResponse.user().role().name());
        return new AuthResponse(authResponse.user(), token);
    }
}