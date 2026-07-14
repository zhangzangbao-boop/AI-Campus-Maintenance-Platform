package com.qiyun.feign.client;

import com.qiyun.feign.dto.UserSummaryDto;
import com.qiyun.feign.dto.UserValidationDto;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户服务内部接口 Feign 客户端
 * 仅供微服务内部调用，不通过 Gateway 公开
 */
@FeignClient(name = "qiyun-user-service", path = "/internal/users")
public interface UserServiceClient {

    /**
     * 验证用户
     * GET /internal/users/{userId}/validation
     */
    @GetMapping("/{userId}/validation")
    UserValidationDto validateUser(@PathVariable("userId") String userId);

    /**
     * 获取用户摘要
     * GET /internal/users/{userId}/summary
     */
    @GetMapping("/{userId}/summary")
    UserSummaryDto getUserSummary(@PathVariable("userId") String userId);

    /**
     * 获取可用维修工列表
     * GET /internal/users/staff/available
     */
    @GetMapping("/staff/available")
    List<UserSummaryDto> getAvailableStaff();

    /**
     * 按角色获取用户列表
     * GET /internal/users?role={role}
     */
    @GetMapping(params = "role")
    List<UserSummaryDto> getUsersByRole(@RequestParam("role") String role);

    /**
     * 批量获取用户摘要
     * GET /internal/users/batch?ids={ids}
     */
    @GetMapping("/batch")
    List<UserSummaryDto> getUsersBatch(@RequestParam("ids") List<String> userIds);
}