package com.qiyun.opsservice.websocket;

import com.qiyun.opsservice.util.JwtUtil;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * WebSocket JWT 认证拦截器
 * 在 STOMP CONNECT 时验证 JWT Token
 * 在 SUBSCRIBE 时校验用户隔离：只能订阅自己的通知频道
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    // 匹配 /topic/user/{userId} 格式的目的地
    private static final Pattern USER_TOPIC_PATTERN = Pattern.compile("^/topic/user/([^/]+)$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (accessor.getCommand() == null) {
            return message;
        }

        // 处理 CONNECT 命令：验证 JWT
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
            // 返回带有认证信息的消息
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        }

        // 处理 SUBSCRIBE 命令：校验用户隔离
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }

        return message;
    }

    /**
     * 处理 CONNECT：验证 JWT 并提取用户身份
     */
    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");

        if (authHeader == null || authHeader.isBlank()) {
            log.warn("WebSocket 连接缺少 Authorization 头");
            throw new IllegalArgumentException("缺少认证信息");
        }

        // 提取 Token（支持 Bearer 前缀）
        String token = authHeader;
        if (authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 验证 Token
        if (!jwtUtil.validateToken(token)) {
            log.warn("WebSocket 连接 Token 验证失败");
            throw new IllegalArgumentException("Token 无效或已过期");
        }

        // 提取用户信息
        String userId = jwtUtil.extractUserId(token);
        List<String> roles = jwtUtil.extractRoles(token);

        if (userId == null || userId.isBlank()) {
            log.warn("WebSocket 连接 Token 中缺少用户信息");
            throw new IllegalArgumentException("Token 中缺少用户信息");
        }

        // 创建认证对象
        List<SimpleGrantedAuthority> authorities = roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userId, null, authorities);

        // 设置用户主体，用于后续消息处理
        accessor.setUser(authentication);

        log.info("WebSocket 用户认证成功: userId={}, roles={}", userId, roles);
    }

    /**
     * 处理 SUBSCRIBE：校验用户只能订阅自己的频道
     */
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        // 检查是否订阅用户通知频道
        Matcher matcher = USER_TOPIC_PATTERN.matcher(destination);
        if (matcher.matches()) {
            String targetUserId = matcher.group(1);
            String currentUserId = getCurrentUserId(accessor);

            if (currentUserId == null) {
                log.warn("WebSocket SUBSCRIBE 未认证用户尝试订阅: {}", destination);
                throw new IllegalArgumentException("未认证用户");
            }

            if (!currentUserId.equals(targetUserId)) {
                log.warn("WebSocket SUBSCRIBE 用户隔离违规: userId={} 尝试订阅 {}",
                    currentUserId, destination);
                throw new IllegalArgumentException("禁止订阅他人频道");
            }

            log.debug("WebSocket SUBSCRIBE 用户订阅验证通过: userId={}", currentUserId);
        }
    }

    /**
     * 从认证信息中获取当前用户ID
     */
    private String getCurrentUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            return auth.getPrincipal() != null ? auth.getPrincipal().toString() : null;
        }
        return null;
    }
}