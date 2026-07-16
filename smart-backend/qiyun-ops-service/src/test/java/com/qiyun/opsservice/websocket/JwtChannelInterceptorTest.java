package com.qiyun.opsservice.websocket;

import com.qiyun.opsservice.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * WebSocket JWT 认证拦截器测试
 */
@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    private JwtChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new JwtChannelInterceptor(jwtUtil);
    }

    // ========== CONNECT 测试 ==========

    @Test
    @DisplayName("有效Token认证成功")
    void testValidTokenAuthentication() {
        // 模拟有效 Token
        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn("user123");
        when(jwtUtil.extractRoles("valid-token")).thenReturn(List.of("STUDENT"));

        // 创建 STOMP CONNECT 消息
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        Message<byte[]> message = new GenericMessage<>(new byte[0], accessor.getMessageHeaders());

        // 执行拦截
        Message<?> result = interceptor.preSend(message, null);

        assertNotNull(result);
        verify(jwtUtil).validateToken("valid-token");
        verify(jwtUtil).extractUserId("valid-token");
    }

    @Test
    @DisplayName("无效Token抛出异常")
    void testInvalidTokenThrowsException() {
        // 模拟无效 Token
        when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

        // 创建 STOMP CONNECT 消息
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer invalid-token");
        Message<byte[]> message = new GenericMessage<>(new byte[0], accessor.getMessageHeaders());

        // 执行拦截，期望抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            interceptor.preSend(message, null);
        });
    }

    @Test
    @DisplayName("缺少Token抛出异常")
    void testMissingTokenThrowsException() {
        // 创建没有 Authorization 头的消息
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = new GenericMessage<>(new byte[0], accessor.getMessageHeaders());

        // 执行拦截，期望抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            interceptor.preSend(message, null);
        });
    }

    @Test
    @DisplayName("Token无Bearer前缀也能解析")
    void testTokenWithoutBearerPrefix() {
        // 模拟有效 Token
        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn("user456");
        when(jwtUtil.extractRoles("valid-token")).thenReturn(List.of("ADMIN"));

        // 创建不带 Bearer 前缀的消息
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "valid-token");
        Message<byte[]> message = new GenericMessage<>(new byte[0], accessor.getMessageHeaders());

        // 执行拦截
        Message<?> result = interceptor.preSend(message, null);

        assertNotNull(result);
        verify(jwtUtil).validateToken("valid-token");
    }

    @Test
    @DisplayName("非CONNECT消息直接通过")
    void testNonConnectMessagePassesThrough() {
        // 创建非 CONNECT 消息（如 SEND）
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        Message<byte[]> message = new GenericMessage<>(new byte[0], accessor.getMessageHeaders());

        // 执行拦截
        Message<?> result = interceptor.preSend(message, null);

        assertNotNull(result);
        // 不应调用 JWT 验证
        verify(jwtUtil, never()).validateToken(anyString());
    }

    // ========== SUBSCRIBE 用户隔离测试 ==========

    @Test
    @DisplayName("订阅自己的频道成功")
    void testSubscribeOwnChannelSuccess() {
        // 订阅自己的频道
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/user/user123");
        // 设置已认证的用户
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user123", null, List.of());
        accessor.setUser(auth);
        Message<byte[]> message = new GenericMessage<>(new byte[0], accessor.getMessageHeaders());

        // 执行拦截
        Message<?> result = interceptor.preSend(message, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("订阅他人频道抛出异常")
    void testSubscribeOtherUserChannelThrowsException() {
        // 尝试 SUBSCRIBE 其他用户的频道
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/user/user456");
        // 设置已认证的用户（与目标频道不一致）
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user123", null, List.of());
        accessor.setUser(auth);
        Message<byte[]> message = new GenericMessage<>(new byte[0], accessor.getMessageHeaders());

        // 执行拦截，期望抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            interceptor.preSend(message, null);
        });
    }

    @Test
    @DisplayName("未认证用户订阅频道抛出异常")
    void testUnauthenticatedSubscribeThrowsException() {
        // 不进行 CONNECT，直接 SUBSCRIBE
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/user/user123");
        // 没有设置用户认证信息
        Message<byte[]> message = new GenericMessage<>(new byte[0], accessor.getMessageHeaders());

        // 执行拦截，期望抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            interceptor.preSend(message, null);
        });
    }

    @Test
    @DisplayName("订阅非用户频道直接通过")
    void testSubscribeNonUserChannelPassesThrough() {
        // 订阅公共频道（非 /topic/user/{userId} 格式）
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/public");
        Message<byte[]> message = new GenericMessage<>(new byte[0], accessor.getMessageHeaders());

        // 执行拦截
        Message<?> result = interceptor.preSend(message, null);

        assertNotNull(result);
    }
}