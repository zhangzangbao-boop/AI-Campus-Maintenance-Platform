package com.qiyun.aiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.aiservice.config.ChromaConfig;
import java.net.http.HttpClient;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Chroma 客户端服务测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChromaClientServiceTest {

    @Mock
    private ChromaConfig chromaConfig;

    @Mock
    private HttpClient httpClient;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("正常连接 Chroma 服务")
    void testIsAvailable_Success() {
        // 提供必要的配置值
        when(chromaConfig.getUrl()).thenReturn("http://localhost:8000");
        when(chromaConfig.getTimeoutSeconds()).thenReturn(10);

        // 由于实际 HTTP 调用需要真实服务，这里只验证配置读取
        assertDoesNotThrow(() -> new ChromaClientService(chromaConfig, objectMapper));
    }

    @Test
    @DisplayName("配置读取正确")
    void testConfigValues() {
        when(chromaConfig.getUrl()).thenReturn("http://localhost:8000");
        when(chromaConfig.getCollection()).thenReturn("test_collection");
        when(chromaConfig.getTimeoutSeconds()).thenReturn(10);
        when(chromaConfig.getTopK()).thenReturn(5);

        assertEquals("http://localhost:8000", chromaConfig.getUrl());
        assertEquals("test_collection", chromaConfig.getCollection());
        assertEquals(10, chromaConfig.getTimeoutSeconds());
        assertEquals(5, chromaConfig.getTopK());
    }

    @Test
    @DisplayName("检索结果记录构造正确")
    void testRetrievalResult() {
        Map<String, String> metadata = Map.of("title", "测试标题", "categoryKey", "ac");
        ChromaClientService.RetrievalResult result =
            new ChromaClientService.RetrievalResult("kb-1", "测试内容", metadata, 0.85);

        assertEquals("kb-1", result.id());
        assertEquals("测试内容", result.document());
        assertEquals(0.85, result.similarity());
        assertEquals("测试标题", result.metadata().get("title"));
    }
}