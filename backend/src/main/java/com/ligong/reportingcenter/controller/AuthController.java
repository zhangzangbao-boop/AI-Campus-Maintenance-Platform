package com.ligong.reportingcenter.controller;

import com.ligong.reportingcenter.dto.UserDto;
import com.ligong.reportingcenter.dto.request.LoginRequest;
import com.ligong.reportingcenter.dto.request.UserRegisterRequest;
import com.ligong.reportingcenter.dto.response.AuthResponse;
import com.ligong.reportingcenter.service.UserService;
import com.ligong.reportingcenter.util.JwtUtil;
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