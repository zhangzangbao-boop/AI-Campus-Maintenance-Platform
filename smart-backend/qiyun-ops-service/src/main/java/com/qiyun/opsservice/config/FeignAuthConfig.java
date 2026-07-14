package com.qiyun.opsservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign请求拦截器 - 转发当前请求的Authorization头
 */
@Slf4j
@Configuration
public class FeignAuthConfig {

    @Bean
    public RequestInterceptor authInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 从当前请求上下文获取Authorization头
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    String authHeader = attributes.getRequest().getHeader("Authorization");
                    if (authHeader != null && !authHeader.isBlank()) {
                        // 转发Authorization头，不记录完整Token
                        log.debug("转发Authorization头到Feign请求");
                        template.header("Authorization", authHeader);
                    } else {
                        log.debug("当前请求无Authorization头，不转发");
                    }
                } else {
                    log.debug("无请求上下文，不转发Authorization");
                }
            }
        };
    }
}