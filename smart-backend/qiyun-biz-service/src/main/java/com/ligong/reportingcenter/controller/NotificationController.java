package com.ligong.reportingcenter.controller;

import com.ligong.reportingcenter.dto.NotificationDto;
import com.qiyun.common.exception.BusinessException;
import com.ligong.reportingcenter.service.NotificationService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Map<String, Object> listMine() {
        List<NotificationDto> notifications = notificationService.listMine(currentUserId());
        return success("获取通知成功", notifications);
    }

    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount() {
        Map<String, Object> data = new HashMap<>();
        data.put("count", notificationService.countUnread(currentUserId()));
        return success("获取未读通知数量成功", data);
    }

    @PutMapping("/{id}/read")
    public Map<String, Object> markRead(@PathVariable("id") Long id) {
        NotificationDto notification = notificationService.markRead(id, currentUserId());
        return success("通知已标记为已读", notification);
    }

    @PutMapping("/read-all")
    public Map<String, Object> markAllRead() {
        Map<String, Object> data = new HashMap<>();
        data.put("updated", notificationService.markAllRead(currentUserId()));
        return success("全部通知已标记为已读", data);
    }

    private Map<String, Object> success(String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录后再访问");
        }
        return authentication.getName();
    }
}
