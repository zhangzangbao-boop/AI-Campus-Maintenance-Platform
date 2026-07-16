package com.qiyun.opsservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.opsservice.dto.NotificationDto;
import com.qiyun.opsservice.websocket.WebSocketPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 内部通知推送接口测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebSocketPushService webSocketPushService;

    // 从测试配置读取密钥
    @Value("${internal.service.secret}")
    private String validSecret;

    @Test
    @DisplayName("单用户推送：认证成功")
    void testPushNotificationSuccess() throws Exception {
        var request = Map.of(
            "userId", "user123",
            "title", "测试通知",
            "content", "这是一条测试通知",
            "relatedOrderId", 100L
        );

        mockMvc.perform(post("/internal/notifications/push")
                .header("X-Internal-Secret", validSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.message").value("推送成功"));

        verify(webSocketPushService).pushNotification(anyString(), any(NotificationDto.class));
    }

    @Test
    @DisplayName("单用户推送：认证失败")
    void testPushNotificationUnauthorized() throws Exception {
        var request = Map.of(
            "userId", "user123",
            "title", "测试通知",
            "content", "这是一条测试通知"
        );

        mockMvc.perform(post("/internal/notifications/push")
                .header("X-Internal-Secret", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));

        verify(webSocketPushService, never()).pushNotification(anyString(), any());
    }

    @Test
    @DisplayName("单用户推送：缺少密钥")
    void testPushNotificationMissingSecret() throws Exception {
        var request = Map.of(
            "userId", "user123",
            "title", "测试通知",
            "content", "这是一条测试通知"
        );

        mockMvc.perform(post("/internal/notifications/push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());

        verify(webSocketPushService, never()).pushNotification(anyString(), any());
    }

    @Test
    @DisplayName("单用户推送：用户ID为空")
    void testPushNotificationEmptyUserId() throws Exception {
        var request = Map.of(
            "userId", "",
            "title", "测试通知",
            "content", "这是一条测试通知"
        );

        mockMvc.perform(post("/internal/notifications/push")
                .header("X-Internal-Secret", validSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("批量推送：认证成功")
    void testPushNotificationBatchSuccess() throws Exception {
        var request = Map.of(
            "userIds", List.of("user1", "user2", "user3"),
            "title", "批量测试通知",
            "content", "这是一条批量测试通知",
            "relatedOrderId", 200L
        );

        mockMvc.perform(post("/internal/notifications/push-batch")
                .header("X-Internal-Secret", validSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.pushedCount").value(3));

        verify(webSocketPushService).pushNotificationToMany(any(), any(NotificationDto.class));
    }

    @Test
    @DisplayName("批量推送：认证失败")
    void testPushNotificationBatchUnauthorized() throws Exception {
        var request = Map.of(
            "userIds", List.of("user1", "user2"),
            "title", "批量测试通知",
            "content", "这是一条批量测试通知"
        );

        mockMvc.perform(post("/internal/notifications/push-batch")
                .header("X-Internal-Secret", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());

        verify(webSocketPushService, never()).pushNotificationToMany(any(), any());
    }

    @Test
    @DisplayName("批量推送：用户列表为空")
    void testPushNotificationBatchEmptyUserIds() throws Exception {
        var request = Map.of(
            "userIds", List.of(),
            "title", "批量测试通知",
            "content", "这是一条批量测试通知"
        );

        mockMvc.perform(post("/internal/notifications/push-batch")
                .header("X-Internal-Secret", validSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }
}