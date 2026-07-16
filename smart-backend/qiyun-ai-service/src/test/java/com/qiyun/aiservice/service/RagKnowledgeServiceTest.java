package com.qiyun.aiservice.service;

import com.qiyun.aiservice.config.ChromaConfig;
import com.qiyun.aiservice.config.DeepSeekConfig;
import com.qiyun.aiservice.service.ChromaClientService.RetrievalResult;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RAG 知识库服务测试
 */
@ExtendWith(MockitoExtension.class)
class RagKnowledgeServiceTest {

    @Mock
    private ChromaClientService chromaClientService;

    @Mock
    private DeepSeekClientService deepSeekClientService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private ChromaConfig chromaConfig;

    @Mock
    private DeepSeekConfig deepSeekConfig;

    private RagKnowledgeService ragKnowledgeService;

    @BeforeEach
    void setUp() {
        ragKnowledgeService = new RagKnowledgeService(
            chromaClientService, deepSeekClientService, embeddingService, chromaConfig, deepSeekConfig
        );
    }

    @Test
    @DisplayName("空问题返回错误")
    void testEmptyQuestion() {
        RagKnowledgeService.RagAnswer answer = ragKnowledgeService.ask("", null);
        assertFalse(answer.success());
        assertEquals("问题不能为空", answer.message());
    }

    @Test
    @DisplayName("Embedding 服务不可用时返回降级提示")
    void testEmbeddingUnavailable() {
        when(embeddingService.isAvailable()).thenReturn(false);

        RagKnowledgeService.RagAnswer answer = ragKnowledgeService.ask("空调不制冷怎么办？", null);
        assertFalse(answer.success());
        assertTrue(answer.fallback());
        assertEquals("Embedding 服务暂不可用，请联系管理员配置模型", answer.message());
    }

    @Test
    @DisplayName("Chroma 不可用时返回降级提示")
    void testChromaUnavailable() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(chromaClientService.isAvailable()).thenReturn(false);

        RagKnowledgeService.RagAnswer answer = ragKnowledgeService.ask("空调不制冷怎么办？", null);
        assertFalse(answer.success());
        assertTrue(answer.fallback());
        assertEquals("向量知识库暂不可用，请联系管理员", answer.message());
    }

    @Test
    @DisplayName("无匹配结果时返回提示")
    void testNoMatch() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.getDimensions()).thenReturn(384);
        when(embeddingService.embed(anyString())).thenReturn(new float[384]);
        when(chromaClientService.isAvailable()).thenReturn(true);
        when(chromaClientService.ensureCollection()).thenReturn(true);
        when(chromaClientService.queryWithEmbedding(any(float[].class), anyInt(), any())).thenReturn(List.of());

        RagKnowledgeService.RagAnswer answer = ragKnowledgeService.ask("这是什么？", null);
        assertFalse(answer.success());
        assertEquals("未找到相关维修知识，请联系管理员或提交报修工单", answer.message());
    }

    @Test
    @DisplayName("低相似度结果被过滤")
    void testLowSimilarityFiltered() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.getDimensions()).thenReturn(384);
        when(embeddingService.embed(anyString())).thenReturn(new float[384]);
        when(chromaClientService.isAvailable()).thenReturn(true);
        when(chromaClientService.ensureCollection()).thenReturn(true);

        RetrievalResult lowSimilarity = new RetrievalResult("kb-1", "测试内容", Map.of("title", "测试"), 0.1);
        when(chromaClientService.queryWithEmbedding(any(float[].class), anyInt(), any())).thenReturn(List.of(lowSimilarity));

        RagKnowledgeService.RagAnswer answer = ragKnowledgeService.ask("空调不制冷怎么办？", null);
        assertFalse(answer.success());
        assertEquals("未找到相关维修知识，请联系管理员或提交报修工单", answer.message());
    }

    @Test
    @DisplayName("AI 不可用时返回降级回答")
    void testFallbackAnswer() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.getDimensions()).thenReturn(384);
        when(embeddingService.embed(anyString())).thenReturn(new float[384]);
        when(chromaClientService.isAvailable()).thenReturn(true);
        when(chromaClientService.ensureCollection()).thenReturn(true);
        when(deepSeekClientService.isAvailable()).thenReturn(false);

        Map<String, String> metadata = Map.of("title", "空调故障处理", "categoryKey", "ac");
        RetrievalResult result = new RetrievalResult("kb-1", "检查空调滤网是否堵塞", metadata, 0.85);
        when(chromaClientService.queryWithEmbedding(any(float[].class), anyInt(), any())).thenReturn(List.of(result));

        RagKnowledgeService.RagAnswer answer = ragKnowledgeService.ask("空调不制冷怎么办？", null);

        assertTrue(answer.success());
        assertTrue(answer.fallback());
        assertNotNull(answer.answer());
        assertFalse(answer.sources().isEmpty());
        assertEquals(0.85, answer.maxSimilarity());
    }

    @Test
    @DisplayName("分类过滤参数正确传递")
    void testCategoryFilter() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.getDimensions()).thenReturn(384);
        when(embeddingService.embed(anyString())).thenReturn(new float[384]);
        when(chromaClientService.isAvailable()).thenReturn(true);
        when(chromaClientService.ensureCollection()).thenReturn(true);
        when(chromaClientService.queryWithEmbedding(any(float[].class), anyInt(), any())).thenReturn(List.of());

        ragKnowledgeService.ask("空调问题", "ac");

        verify(chromaClientService).queryWithEmbedding(any(float[].class), anyInt(), argThat(filter ->
            filter != null && filter.containsKey("categoryKey") && "ac".equals(filter.get("categoryKey"))
        ));
    }

    @Test
    @DisplayName("回答包含来源信息")
    void testAnswerContainsSources() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.getDimensions()).thenReturn(384);
        when(embeddingService.embed(anyString())).thenReturn(new float[384]);
        when(chromaClientService.isAvailable()).thenReturn(true);
        when(chromaClientService.ensureCollection()).thenReturn(true);
        when(deepSeekClientService.isAvailable()).thenReturn(false);

        Map<String, String> metadata = Map.of("title", "漏水处理", "categoryKey", "plumbing");
        RetrievalResult result = new RetrievalResult("kb-5", "关闭总阀门，检查漏水点", metadata, 0.92);
        when(chromaClientService.queryWithEmbedding(any(float[].class), anyInt(), any())).thenReturn(List.of(result));

        RagKnowledgeService.RagAnswer answer = ragKnowledgeService.ask("水管漏水", "plumbing");

        assertTrue(answer.success());
        assertEquals(1, answer.sources().size());
        assertEquals("kb-5", answer.sources().get(0).id());
        assertEquals("漏水处理", answer.sources().get(0).title());
        assertEquals(0.92, answer.sources().get(0).similarity());
    }
}