package com.qiyun.userservice.controller;

import com.qiyun.userservice.dto.PagedResult;
import com.qiyun.userservice.dto.ResetPasswordResult;
import com.qiyun.userservice.dto.UserDto;
import com.qiyun.userservice.dto.request.UserRegisterRequest;
import com.qiyun.userservice.dto.request.UserUpdateRequest;
import com.qiyun.userservice.service.UserService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户管理控制器
 * 处理 /api/admin/users/** 路径的用户管理请求
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResult<UserDto> listUsers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        List<UserDto> allUsers = userService.listAll();

        // 简单分页模拟
        int total = allUsers.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<UserDto> pagedUsers = allUsers.subList(fromIndex, toIndex);

        return new PagedResult<>(pagedUsers, total);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto createUser(@RequestBody UserRegisterRequest request) {
        UserDto user = userService.register(request);
        log.info("管理员创建用户: userId={}, nickname={}, role={}", user.userId(), user.nickname(), user.role());
        return user;
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto updateUser(@PathVariable("userId") String userId, @RequestBody UserUpdateRequest request) {
        UserDto user = userService.updateUser(userId, request);
        log.info("管理员更新用户: userId={}, nickname={}", userId, user.nickname());
        return user;
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable("userId") String userId) {
        userService.deactivate(userId);
        log.info("管理员禁用用户: userId={}", userId);
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> resetPassword(@PathVariable("userId") String userId) {
        try {
            ResetPasswordResult result = userService.resetPassword(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "密码重置成功");
            response.put("userId", result.getUserId());
            response.put("newPassword", result.getNewPassword());
            log.info("管理员重置用户密码: userId={}", userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
