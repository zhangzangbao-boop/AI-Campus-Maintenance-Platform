package com.qiyun.aiservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 服务健康检查接口
 */
@RestController
@RequestMapping("/api/ai")
public class AiHealthController {

    @GetMapping("/health")
    public String health() {
        return "qiyun-ai-service is running";
    }
}