package com.qiyun.opsservice.config;

import com.qiyun.opsservice.websocket.JwtChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置
 * 使用 STOMP 协议，支持 JWT 认证
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单的内存消息代理
        // 客户端订阅 /topic/{userId} 接收个人通知
        config.enableSimpleBroker("/topic");
        // 客户端发送消息的前缀（本系统主要由服务端推送，客户端较少发送）
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 端点，前端通过此端点建立连接
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*") // 允许所有来源（生产环境应限制）
            .withSockJS(); // 启用 SockJS 降级支持
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 在入站通道添加 JWT 认证拦截器
        registration.interceptors(jwtChannelInterceptor);
    }
}