package com.qiyun.feign.client;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * AI 服务内部接口 Feign 客户端
 * 用于服务间调用 AI 服务的内部接口
 */
@FeignClient(name = "qiyun-ai-service", contextId = "aiInternalClient", path = "/internal")
public interface AiInternalClient {

    /**
     * 同步知识条目到向量库
     */
    @PostMapping("/rag/knowledge/{id}")
    Map<String, Object> syncKnowledge(
        @RequestHeader("X-Internal-Secret") String secret,
        @PathVariable("id") Long id,
        @RequestBody KnowledgeSyncRequest request
    );

    /**
     * 从向量库删除知识条目
     */
    @DeleteMapping("/rag/knowledge/{id}")
    Map<String, Object> deleteKnowledge(
        @RequestHeader("X-Internal-Secret") String secret,
        @PathVariable("id") Long id
    );

    /**
     * 清空并重建向量索引
     */
    @PostMapping("/rag/rebuild")
    Map<String, Object> rebuildIndex(
        @RequestHeader("X-Internal-Secret") String secret
    );

    /**
     * 知识同步请求
     */
    record KnowledgeSyncRequest(
        String document,
        String title,
        String categoryKey,
        Boolean enabled
    ) {}
}