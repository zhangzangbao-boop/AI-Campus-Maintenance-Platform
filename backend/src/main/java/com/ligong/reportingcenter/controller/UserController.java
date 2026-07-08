package com.ligong.reportingcenter.controller;

import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.UserDto;
import com.ligong.reportingcenter.dto.request.ChangePasswordRequest;
import com.ligong.reportingcenter.dto.request.UserUpdateRequest;
import com.ligong.reportingcenter.exception.BusinessException;
import com.ligong.reportingcenter.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserDto getCurrentUser() {
        // 从Security Context中获取当前用户ID
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.getCurrentUser(currentUserId);
    }

    @PutMapping("/me")
    public UserDto updateUserInfo(@RequestBody UserUpdateRequest request) {
        // 从Security Context中获取当前用户ID
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.updateUserInfo(currentUserId, request);
    }

    @PutMapping("/me/password")
    public Map<String, Object> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.changePassword(currentUserId, request);
        return Map.of(
            "code", 200,
            "message", "密码修改成功，请使用新密码登录",
            "data", true
        );
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto getUser(@PathVariable("userId") String userId) {
        return userService.findById(userId);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> listUsers(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        
        List<UserDto> users;
        
        // 根据角色筛选
        if (role != null && !role.isBlank()) {
            try {
                UserRole userRole = UserRole.valueOf(role.toUpperCase());
                users = userService.listByRole(userRole);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("无效的角色类型");
            }
        } else {
            users = userService.listAll();
        }
        
        // 关键词搜索（支持 nickname、userId、contactPhone 字段）
        if (keyword != null && !keyword.isBlank()) {
            String lowerKeyword = keyword.toLowerCase();
            users = users.stream()
                .filter(user -> {
                    String userNickname = user.nickname() != null ? user.nickname().toLowerCase() : "";
                    String userUserId = user.userId() != null ? user.userId().toLowerCase() : "";
                    String userPhone = user.contactPhone() != null ? user.contactPhone().toLowerCase() : "";
                    return userNickname.contains(lowerKeyword)
                        || userUserId.contains(lowerKeyword)
                        || userPhone.contains(lowerKeyword);
                })
                .collect(java.util.stream.Collectors.toList());
        }
        
        // 单独字段搜索（优先级高于 keyword）
        if (name != null && !name.isBlank()) {
            String lowerName = name.toLowerCase();
            users = users.stream()
                .filter(user -> {
                    String userNickname = user.nickname() != null ? user.nickname().toLowerCase() : "";
                    return userNickname.contains(lowerName);
                })
                .collect(java.util.stream.Collectors.toList());
        }
        
        if (userId != null && !userId.isBlank()) {
            String lowerUserId = userId.toLowerCase();
            users = users.stream()
                .filter(user -> user.userId() != null && user.userId().toLowerCase().contains(lowerUserId))
                .collect(java.util.stream.Collectors.toList());
        }
        
        return users;
    }

    @PatchMapping("/{userId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable("userId") @NotBlank String userId) {
        userService.deactivate(userId);
    }
    
    @PatchMapping("/{userId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@PathVariable("userId") @NotBlank String userId) {
        userService.activate(userId);
    }
}
