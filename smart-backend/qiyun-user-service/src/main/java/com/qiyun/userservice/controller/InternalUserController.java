package com.qiyun.userservice.controller;

import com.qiyun.feign.dto.UserSummaryDto;
import com.qiyun.feign.dto.UserValidationDto;
import com.qiyun.userservice.domain.enums.UserRole;
import com.qiyun.userservice.dto.UserDto;
import com.qiyun.userservice.service.UserService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户服务内部接口控制器
 * 仅供微服务内部调用，不通过 Gateway 公开路由
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{userId}/validation")
    public UserValidationDto validateUser(@PathVariable("userId") String userId) {
        return userService.findByIdOptional(userId)
            .map(user -> new UserValidationDto(true, user.active(), user.role().name()))
            .orElse(new UserValidationDto(false, false, null));
    }

    @GetMapping("/{userId}/summary")
    public UserSummaryDto getUserSummary(@PathVariable("userId") String userId) {
        return userService.findByIdOptional(userId)
            .map(this::toSummaryDto)
            .orElse(null);
    }

    @GetMapping("/staff/available")
    public List<UserSummaryDto> getAvailableStaff() {
        return userService.listByRole(UserRole.STAFF).stream()
            .filter(user -> user.active())
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    @GetMapping(params = "role")
    public List<UserSummaryDto> getUsersByRole(@RequestParam("role") String role) {
        UserRole userRole = UserRole.valueOf(role.toUpperCase());
        return userService.listByRole(userRole).stream()
            .filter(user -> user.active())
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    @GetMapping("/batch")
    public List<UserSummaryDto> getUsersBatch(@RequestParam("ids") List<String> userIds) {
        return userIds.stream()
            .map(id -> userService.findByIdOptional(id))
            .filter(opt -> opt.isPresent())
            .map(opt -> opt.get())
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    private UserSummaryDto toSummaryDto(UserDto user) {
        boolean staff = user.role() == UserRole.STAFF;
        return new UserSummaryDto(
            user.userId(),
            user.nickname(),
            user.role().name(),
            user.active(),
            user.contactPhone(),
            staff ? user.responsibleArea() : null,
            staff ? user.specialties() : null
        );
    }
}
