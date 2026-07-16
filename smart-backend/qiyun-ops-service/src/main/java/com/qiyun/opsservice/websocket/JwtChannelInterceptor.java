package com.qiyun.opsservice.websocket;

import com.qiyun.opsservice.util.JwtUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * WebSocket JWT 认证拦截器
 * 在 STOMP CONNECT 时验证 JWT Token
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // 只处理 CONNECT 命令
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 从请求头获取 Authorization
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

        return message;
    }
}