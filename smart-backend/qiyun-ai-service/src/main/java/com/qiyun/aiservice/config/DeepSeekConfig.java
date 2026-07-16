package com.qiyun.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * DeepSeek AI 配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
public class DeepSeekConfig {

    /**
     * 是否启用 AI 服务
     */
    private boolean enabled = false;

    /**
     * AI 提供商名称
     */
    private String provider = "deepseek";

    /**
     * API 基础 URL
     */
    private String baseUrl = "https://api.deepseek.com";

    /**
     * API 密钥（通过环境变量 DEEPSEEK_API_KEY 注入）
     */
    private String apiKey;

    /**
     * 模型名称
     */
    private String model = "deepseek-chat";

    /**
     * 请求超时时间（秒）
     */
    private int timeoutSeconds = 20;

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}