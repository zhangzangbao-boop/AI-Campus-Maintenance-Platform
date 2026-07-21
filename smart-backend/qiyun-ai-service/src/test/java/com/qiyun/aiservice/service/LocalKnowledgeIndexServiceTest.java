package com.qiyun.aiservice.service;

import com.qiyun.aiservice.service.ChromaClientService.RetrievalResult;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalKnowledgeIndexServiceTest {

    @Test
    @DisplayName("本地知识索引可按中文问题检索维修知识")
    void searchChineseQuestion() {
        LocalKnowledgeIndexService service = new LocalKnowledgeIndexService();
        service.upsertKnowledge(
            "kb-1",
            "【标题】宿舍漏水处理\n【故障症状】宿舍卫生间漏水、地面积水\n【解决步骤】先关闭水阀，清理积水，提交报修并上传照片。",
            Map.of("title", "宿舍漏水处理", "categoryKey", "plumbing", "enabled", "true")
        );

        List<RetrievalResult> results = service.search("宿舍漏水怎么处理？", 3, "plumbing");

        assertFalse(results.isEmpty());
        assertEquals("宿舍漏水处理", results.get(0).metadata().get("title"));
        assertTrue(results.get(0).similarity() > 0.1);
    }

    @Test
    @DisplayName("禁用知识条目会从本地索引删除")
    void disabledKnowledgeRemoved() {
        LocalKnowledgeIndexService service = new LocalKnowledgeIndexService();
        service.upsertKnowledge("kb-1", "空调不制冷处理", Map.of("title", "空调处理", "enabled", "true"));
        service.upsertKnowledge("kb-1", "空调不制冷处理", Map.of("title", "空调处理", "enabled", "false"));

        assertEquals(0, service.size());
        assertTrue(service.search("空调不制冷", 3, null).isEmpty());
    }

    @Test
    @DisplayName("本地知识索引支持前端分类键匹配中文知识分类")
    void searchWithCategoryAlias() {
        LocalKnowledgeIndexService service = new LocalKnowledgeIndexService();
        service.upsertKnowledge(
            "kb-1",
            "【标题】校园网连接不上处理\n【故障症状】网络、校园网、无法连接、认证失败\n【解决步骤】检查账号、网线和网口，保留错误截图后提交报修。",
            Map.of("title", "校园网连接不上处理", "categoryKey", "网络故障", "enabled", "true")
        );

        List<RetrievalResult> results = service.search("网络连接不上怎么办？", 3, "network");

        assertFalse(results.isEmpty());
        assertEquals("校园网连接不上处理", results.get(0).metadata().get("title"));
    }
}
