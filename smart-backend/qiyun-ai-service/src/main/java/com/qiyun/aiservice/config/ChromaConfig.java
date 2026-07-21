package com.qiyun.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Chroma 向量数据库配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "chroma")
public class ChromaConfig {

    /**
     * Chroma 服务地址
     */
    private String url = "http://localhost:8000";

    /**
     * 集合名称
     */
    private String collection = "campus_maintenance_kb";

    /**
     * Chroma v2 tenant
     */
    private String tenant = "default_tenant";

    /**
     * Chroma v2 database
     */
    private String database = "default_database";

    /**
     * 检索返回数量
     */
    private int topK = 5;

    /**
     * 连接超时时间（秒）
     */
    private int timeoutSeconds = 10;

    // Getters and Setters
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
