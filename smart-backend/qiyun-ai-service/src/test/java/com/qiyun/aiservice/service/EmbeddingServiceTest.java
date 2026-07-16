package com.qiyun.aiservice.service;

import com.qiyun.aiservice.config.EmbeddingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Embedding 服务测试
 */
@DisplayName("Embedding 服务测试")
class EmbeddingServiceTest {

    private EmbeddingConfig config;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        config = new EmbeddingConfig();
        config.setEnabled(true);
        config.setDimensions(384);
        config.setMaxSequenceLength(512);
        config.setBatchSize(32);
        // 模型路径从环境变量获取，测试时可以不设置
        config.setModelPath(System.getenv("EMBEDDING_MODEL_PATH"));

        embeddingService = new EmbeddingService(config);
    }

    @Test
    @DisplayName("服务不可用时返回 false")
    void testNotAvailableWithoutModel() {
        // 如果没有配置模型路径，服务应该不可用
        String modelPath = System.getenv("EMBEDDING_MODEL_PATH");
        if (modelPath == null || modelPath.isBlank()) {
            assertFalse(embeddingService.isAvailable());
        }
    }

    @Test
    @DisplayName("禁用时服务不可用")
    void testDisabled() {
        EmbeddingConfig disabledConfig = new EmbeddingConfig();
        disabledConfig.setEnabled(false);
        EmbeddingService disabledService = new EmbeddingService(disabledConfig);

        assertFalse(disabledService.isAvailable());
    }

    @Test
    @DisplayName("空文本返回 null")
    void testEmbedEmptyText() {
        // 即使服务不可用，空文本也应该返回 null
        assertNull(embeddingService.embed(null));
        assertNull(embeddingService.embed(""));
        assertNull(embeddingService.embed("   "));
    }

    @Test
    @DisplayName("批量 embed 处理空列表")
    void testEmbedBatchEmpty() {
        List<float[]> result = embeddingService.embedBatch(List.of());
        assertTrue(result.isEmpty());

        result = embeddingService.embedBatch(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("获取维度配置")
    void testGetDimensions() {
        assertEquals(384, embeddingService.getDimensions());
    }

    @Test
    @DisplayName("中文文本 embed - 需要模型")
    void testChineseTextEmbedding() {
        // 仅在有模型时运行
        if (!embeddingService.isAvailable()) {
            return;
        }

        String text = "空调不制冷";
        float[] embedding = embeddingService.embed(text);

        assertNotNull(embedding);
        assertEquals(384, embedding.length);

        // 验证向量已归一化
        float norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        assertEquals(1.0, Math.sqrt(norm), 0.01, "向量应该已归一化");
    }

    @Test
    @DisplayName("中文语义相似度测试 - 需要模型")
    void testChineseSemanticSimilarity() {
        // 仅在有模型时运行
        if (!embeddingService.isAvailable()) {
            return;
        }

        // 相似的语义
        String text1 = "空调不制冷";
        String text2 = "宿舍空调制冷故障处理";
        String text3 = "水管漏水维修";

        float[] emb1 = embeddingService.embed(text1);
        float[] emb2 = embeddingService.embed(text2);
        float[] emb3 = embeddingService.embed(text3);

        assertNotNull(emb1);
        assertNotNull(emb2);
        assertNotNull(emb3);

        // 计算相似度（余弦相似度，已归一化所以直接点积）
        double sim12 = cosineSimilarity(emb1, emb2);
        double sim13 = cosineSimilarity(emb1, emb3);

        System.out.println("相似度 '" + text1 + "' vs '" + text2 + "': " + sim12);
        System.out.println("相似度 '" + text1 + "' vs '" + text3 + "': " + sim13);

        // 空调相关应该比水管相关更相似
        assertTrue(sim12 > sim13,
            "'" + text1 + "' 与 '" + text2 + "' 应该比与 '" + text3 + "' 更相似");

        // 阈值检查（相似文本应该有合理的相似度）
        assertTrue(sim12 > 0.5,
            "语义相似的文本相似度应该 > 0.5，实际: " + sim12);
    }

    @Test
    @DisplayName("批量处理中文文本")
    void testBatchChineseEmbedding() {
        if (!embeddingService.isAvailable()) {
            return;
        }

        List<String> texts = List.of(
            "空调不制冷",
            "电灯坏了",
            "网络连接不上",
            "水管漏水"
        );

        List<float[]> embeddings = embeddingService.embedBatch(texts);

        assertEquals(4, embeddings.size());
        for (float[] emb : embeddings) {
            assertNotNull(emb);
            assertEquals(384, emb.length);
        }
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致");
        }

        double dotProduct = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
        }

        // 已归一化，直接返回点积
        return dotProduct;
    }
}