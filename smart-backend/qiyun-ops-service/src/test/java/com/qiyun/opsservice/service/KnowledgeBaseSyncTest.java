package com.qiyun.opsservice.service;

import com.qiyun.feign.client.AiInternalClient;
import com.qiyun.opsservice.domain.entity.KnowledgeBase;
import com.qiyun.opsservice.repository.KnowledgeBaseRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 知识库服务测试
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseSyncTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AiInternalClient aiInternalClient;

    private KnowledgeBaseService knowledgeBaseService;

    @BeforeEach
    void setUp() {
        knowledgeBaseService = new KnowledgeBaseService(
            knowledgeBaseRepository, auditLogService, aiInternalClient
        );
        ReflectionTestUtils.setField(knowledgeBaseService, "internalSecret", "test-secret");
    }

    @Test
    @DisplayName("创建启用知识条目时同步到向量库")
    void testCreateEnabledSyncsToChroma() {
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class))).thenAnswer(inv -> {
            KnowledgeBase kb = inv.getArgument(0);
            kb.setKnowledgeId(1L);
            return kb;
        });
        when(aiInternalClient.syncKnowledge(anyString(), any(), any())).thenReturn(Map.of("code", 200));

        var request = new com.qiyun.opsservice.dto.request.KnowledgeBaseRequest(
            "ac", "空调故障处理", "检查滤网，清洗过滤网", "空调,不制冷", true
        );

        var result = knowledgeBaseService.create(request);

        assertNotNull(result);
        verify(aiInternalClient).syncKnowledge(eq("test-secret"), eq(1L), any());
    }

    @Test
    @DisplayName("创建禁用知识条目不同步向量库")
    void testCreateDisabledNotSynced() {
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class))).thenAnswer(inv -> {
            KnowledgeBase kb = inv.getArgument(0);
            kb.setKnowledgeId(1L);
            return kb;
        });

        var request = new com.qiyun.opsservice.dto.request.KnowledgeBaseRequest(
            "ac", "空调故障处理", "检查滤网", "空调", false
        );

        var result = knowledgeBaseService.create(request);

        assertNotNull(result);
        verify(aiInternalClient, never()).syncKnowledge(anyString(), any(), any());
    }

    @Test
    @DisplayName("更新为启用状态时同步向量库")
    void testUpdateToEnabledSyncs() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setKnowledgeId(1L);
        kb.setTitle("测试");
        kb.setEnabled(false);

        when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));
        when(knowledgeBaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiInternalClient.syncKnowledge(anyString(), any(), any())).thenReturn(Map.of("code", 200));

        var request = new com.qiyun.opsservice.dto.request.KnowledgeBaseRequest(
            "ac", "更新标题", "更新内容", "关键词", true
        );

        var result = knowledgeBaseService.update(1L, request);

        assertTrue(result.enabled());
        verify(aiInternalClient).syncKnowledge(eq("test-secret"), eq(1L), any());
    }

    @Test
    @DisplayName("更新为禁用状态时从向量库删除")
    void testUpdateToDisabledDeletes() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setKnowledgeId(1L);
        kb.setTitle("测试");
        kb.setEnabled(true);

        when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));
        when(knowledgeBaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiInternalClient.deleteKnowledge(anyString(), any())).thenReturn(Map.of("code", 200));

        var request = new com.qiyun.opsservice.dto.request.KnowledgeBaseRequest(
            "ac", "更新标题", "更新内容", "关键词", false
        );

        var result = knowledgeBaseService.update(1L, request);

        assertFalse(result.enabled());
        verify(aiInternalClient).deleteKnowledge("test-secret", 1L);
    }

    @Test
    @DisplayName("删除知识条目时从向量库删除")
    void testDeleteRemovesFromChroma() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setKnowledgeId(1L);
        kb.setTitle("测试");

        when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));
        doNothing().when(knowledgeBaseRepository).delete(any());
        when(aiInternalClient.deleteKnowledge(anyString(), any())).thenReturn(Map.of("code", 200));

        knowledgeBaseService.delete(1L);

        verify(aiInternalClient).deleteKnowledge("test-secret", 1L);
    }

    @Test
    @DisplayName("重建索引清空并重新同步")
    void testRebuildIndex() {
        KnowledgeBase kb1 = new KnowledgeBase();
        kb1.setKnowledgeId(1L);
        kb1.setTitle("知识1");
        kb1.setEnabled(true);

        KnowledgeBase kb2 = new KnowledgeBase();
        kb2.setKnowledgeId(2L);
        kb2.setTitle("知识2");
        kb2.setEnabled(false);

        when(knowledgeBaseRepository.findAllWithCategory()).thenReturn(List.of(kb1, kb2));
        when(aiInternalClient.rebuildIndex(anyString())).thenReturn(Map.of("code", 200));
        when(aiInternalClient.syncKnowledge(anyString(), any(), any())).thenReturn(Map.of("code", 200));

        int count = knowledgeBaseService.rebuildIndex();

        assertEquals(1, count); // 只同步启用的条目
        verify(aiInternalClient).rebuildIndex("test-secret");
        verify(aiInternalClient).syncKnowledge(eq("test-secret"), eq(1L), any());
    }

    @Test
    @DisplayName("推荐知识支持前端分类键匹配中文知识分类")
    void recommendMatchesCategoryAlias() {
        KnowledgeBase network = new KnowledgeBase();
        network.setKnowledgeId(1L);
        network.setTitle("校园网连接不上处理");
        network.setCategoryKey("网络故障");
        network.setSymptomKeywords("网络,校园网,无法连接,认证失败");
        network.setSolutionSteps("检查账号、网线和网口，保留错误截图后提交报修。");
        network.setEnabled(true);
        network.setUpdatedAt(LocalDateTime.now());

        KnowledgeBase plumbing = new KnowledgeBase();
        plumbing.setKnowledgeId(2L);
        plumbing.setTitle("宿舍漏水处理");
        plumbing.setCategoryKey("水电维修");
        plumbing.setSymptomKeywords("漏水,积水");
        plumbing.setSolutionSteps("先关闭水阀并提交报修。");
        plumbing.setEnabled(true);
        plumbing.setUpdatedAt(LocalDateTime.now().minusDays(1));

        when(knowledgeBaseRepository.findAllWithCategory()).thenReturn(List.of(network, plumbing));

        var results = knowledgeBaseService.recommend("network", "网络连接不上怎么办？", 3);

        assertEquals(1, results.size());
        assertEquals("校园网连接不上处理", results.get(0).title());
    }
}
