package com.qiyun.aiservice.controller;

import com.qiyun.aiservice.service.ChromaClientService;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private InternalRagController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalRagController(chromaClientService);
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
    @DisplayName("成功更新向量")
    void testUpsertSuccess() {
        when(chromaClientService.updateDocument(anyString(), anyString(), anyMap())).thenReturn(true);

        InternalRagController.KnowledgeSyncRequest request =
            new InternalRagController.KnowledgeSyncRequest("测试内容", "测试标题", "ac", true);

        ResponseEntity<Map<String, Object>> response =
            controller.upsertKnowledge("test-secret", 1L, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(200, response.getBody().get("code"));
        verify(chromaClientService).updateDocument(eq("kb-1"), eq("测试内容"), anyMap());
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
        when(chromaClientService.updateDocument(anyString(), anyString(), anyMap())).thenReturn(false);

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
}