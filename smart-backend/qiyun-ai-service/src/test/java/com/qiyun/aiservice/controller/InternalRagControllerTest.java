package com.qiyun.aiservice.controller;

import com.qiyun.aiservice.service.ChromaClientService;
import com.qiyun.aiservice.service.EmbeddingService;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 内部 RAG 控制器测试
 */
@ExtendWith(MockitoExtension.class)
class InternalRagControllerTest {

    @Mock
    private ChromaClientService chromaClientService;

    @Mock
    private EmbeddingService embeddingService;

    private InternalRagController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalRagController(chromaClientService, embeddingService);
        ReflectionTestUtils.setField(controller, "internalSecret", "test-secret");
    }

    @Test
    @DisplayName("无效密钥返回401")
    void testInvalidSecret() {
        InternalRagController.KnowledgeSyncRequest request =
            new InternalRagController.KnowledgeSyncRequest("测试内容", "标题", "ac", true);

        ResponseEntity<Map<String, Object>> response = controller.upsertKnowledge(null, 1L, request);

        assertEquals(401, response.getStatusCode().value());
        assertEquals(401, response.getBody().get("code"));
    }

    @Test
    @DisplayName("空文档内容返回400")
    void testEmptyDocument() {
        InternalRagController.KnowledgeSyncRequest request =
            new InternalRagController.KnowledgeSyncRequest("", "标题", "ac", true);

        ResponseEntity<Map<String, Object>> response =
            controller.upsertKnowledge("test-secret", 1L, request);

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Embedding 服务不可用返回503")
    void testEmbeddingServiceUnavailable() {
        when(embeddingService.isAvailable()).thenReturn(false);

        InternalRagController.KnowledgeSyncRequest request =
            new InternalRagController.KnowledgeSyncRequest("测试内容", "测试标题", "ac", true);

        ResponseEntity<Map<String, Object>> response =
            controller.upsertKnowledge("test-secret", 1L, request);

        assertEquals(503, response.getStatusCode().value());
        assertEquals(503, response.getBody().get("code"));
        assertTrue(response.getBody().get("message").toString().contains("Embedding 服务不可用"));
    }

    @Test
    @DisplayName("成功更新向量")
    void testUpsertSuccess() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.getDimensions()).thenReturn(384);
        when(embeddingService.embed(anyString())).thenReturn(new float[384]);
        when(chromaClientService.updateDocument(anyString(), anyString(), any(float[].class), anyMap())).thenReturn(true);

        InternalRagController.KnowledgeSyncRequest request =
            new InternalRagController.KnowledgeSyncRequest("测试内容", "测试标题", "ac", true);

        ResponseEntity<Map<String, Object>> response =
            controller.upsertKnowledge("test-secret", 1L, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(200, response.getBody().get("code"));
        verify(chromaClientService).updateDocument(eq("kb-1"), eq("测试内容"), any(float[].class), anyMap());
    }

    @Test
    @DisplayName("成功删除向量")
    void testDeleteSuccess() {
        when(chromaClientService.deleteDocument(anyString())).thenReturn(true);

        ResponseEntity<Map<String, Object>> response =
            controller.deleteKnowledge("test-secret", 1L);

        assertEquals(200, response.getStatusCode().value());
        verify(chromaClientService).deleteDocument("kb-1");
    }

    @Test
    @DisplayName("成功清空索引")
    void testRebuildSuccess() {
        when(chromaClientService.clearCollection()).thenReturn(true);

        ResponseEntity<Map<String, Object>> response =
            controller.rebuildIndex("test-secret");

        assertEquals(200, response.getStatusCode().value());
        verify(chromaClientService).clearCollection();
    }

    @Test
    @DisplayName("更新失败返回500")
    void testUpsertFailure() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.getDimensions()).thenReturn(384);
        when(embeddingService.embed(anyString())).thenReturn(new float[384]);
        when(chromaClientService.updateDocument(anyString(), anyString(), any(float[].class), anyMap())).thenReturn(false);

        InternalRagController.KnowledgeSyncRequest request =
            new InternalRagController.KnowledgeSyncRequest("测试内容", "测试标题", "ac", true);

        ResponseEntity<Map<String, Object>> response =
            controller.upsertKnowledge("test-secret", 1L, request);

        assertEquals(500, response.getStatusCode().value());
    }

    @Test
    @DisplayName("密钥未配置时拒绝请求")
    void testNoSecretConfigured() {
        ReflectionTestUtils.setField(controller, "internalSecret", "");

        InternalRagController.KnowledgeSyncRequest request =
            new InternalRagController.KnowledgeSyncRequest("测试内容", "标题", "ac", true);

        ResponseEntity<Map<String, Object>> response =
            controller.upsertKnowledge("any-secret", 1L, request);

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Embedding 生成失败返回500")
    void testEmbeddingGenerationFailure() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(null);

        InternalRagController.KnowledgeSyncRequest request =
            new InternalRagController.KnowledgeSyncRequest("测试内容", "测试标题", "ac", true);

        ResponseEntity<Map<String, Object>> response =
            controller.upsertKnowledge("test-secret", 1L, request);

        assertEquals(500, response.getStatusCode().value());
        assertEquals(500, response.getBody().get("code"));
        assertTrue(response.getBody().get("message").toString().contains("生成向量失败"));
    }

    @Test
    @DisplayName("Embedding 维度不匹配返回500")
    void testEmbeddingDimensionMismatch() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.getDimensions()).thenReturn(384);
        when(embeddingService.embed(anyString())).thenReturn(new float[128]); // 错误的维度

        InternalRagController.KnowledgeSyncRequest request =
            new InternalRagController.KnowledgeSyncRequest("测试内容", "测试标题", "ac", true);

        ResponseEntity<Map<String, Object>> response =
            controller.upsertKnowledge("test-secret", 1L, request);

        assertEquals(500, response.getStatusCode().value());
        assertEquals(500, response.getBody().get("code"));
        assertTrue(response.getBody().get("message").toString().contains("向量维度不匹配"));
    }
}