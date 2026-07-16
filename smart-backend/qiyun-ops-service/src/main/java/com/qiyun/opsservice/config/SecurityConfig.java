package com.qiyun.opsservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("timestamp", LocalDateTime.now().toString());
                    body.put("status", HttpStatus.UNAUTHORIZED.value());
                    body.put("error", "Unauthorized");
                    body.put("message", "请先登录后再访问");
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("timestamp", LocalDateTime.now().toString());
                    body.put("status", HttpStatus.FORBIDDEN.value());
                    body.put("error", "Forbidden");
                    body.put("message", "当前账号没有访问该功能的权限");
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                })
            )
            .securityMatcher(request -> !request.getRequestURI().startsWith("/internal/"))
            .authorizeHttpRequests(auth -> auth
                // Actuator
                .requestMatchers("/actuator/**").permitAll()
                // WebSocket 端点
                .requestMatchers("/ws/**").permitAll()
                // 静态资源
                .requestMatchers("/static/**", "/assets/**", "/index.html", "/").permitAll()
                // 知识库公开搜索
                .requestMatchers(HttpMethod.GET, "/api/knowledge-base/search").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/knowledge-base/recommend").permitAll()
                // 其他接口需要认证
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 内部服务接口安全配置（不使用JWT，仅内部密钥认证）
     */
    @Bean
    public SecurityFilterChain internalApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(request -> request.getRequestURI().startsWith("/internal/"))
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}