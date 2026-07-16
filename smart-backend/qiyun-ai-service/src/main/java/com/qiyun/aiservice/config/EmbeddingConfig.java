package com.qiyun.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding 模型配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingConfig {

    /**
     * 是否启用本地 embedding 模型
     */
    private boolean enabled = false;

    /**
     * ONNX 模型路径（环境变量优先）
     */
    private String modelPath;

    private String tokenizerPath;

    /**
     * 模型维度（paraphrase-multilingual-MiniLM-L12-v2 为 384 维）
     */
    private int dimensions = 384;

    /**
     * 最大序列长度
     */
    private int maxSequenceLength = 512;

    /**
     * 批处理大小
     */
    private int batchSize = 32;

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public String getTokenizerPath() {
        return tokenizerPath;
    }

    public void setTokenizerPath(String tokenizerPath) {
        this.tokenizerPath = tokenizerPath;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getMaxSequenceLength() {
        return maxSequenceLength;
    }

    public void setMaxSequenceLength(int maxSequenceLength) {
        this.maxSequenceLength = maxSequenceLength;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
