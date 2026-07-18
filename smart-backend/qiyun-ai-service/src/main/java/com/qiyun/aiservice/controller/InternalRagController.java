package com.qiyun.aiservice.controller;

import com.qiyun.aiservice.service.ChromaClientService;
import com.qiyun.aiservice.service.EmbeddingService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部 RAG 同步接口
 * 仅供服务间调用，需要内部密钥认证
 */
@Slf4j
@RestController
@RequestMapping("/internal/rag")
@RequiredArgsConstructor
public class InternalRagController {

    private final ChromaClientService chromaClientService;
    private final EmbeddingService embeddingService;

    @Value("${internal.service.secret:}")
    private String internalSecret;

    /**
     * 添加/更新知识条目向量
     */
    @PostMapping("/knowledge/{id}")
    public ResponseEntity<Map<String, Object>> upsertKnowledge(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable("id") Long id,
            @RequestBody KnowledgeSyncRequest request) {

        if (!validateSecret(secret)) {
            return ResponseEntity.status(401).body(Map.of(
                "code", 401,
                "message", "认证失败"
            ));
        }

        if (request == null || request.document() == null || request.document().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", "文档内容不能为空"
            ));
        }

        // 检查 Embedding 服务是否可用
        if (!embeddingService.isAvailable()) {
            log.error("Embedding 服务不可用，无法同步知识到向量库");
            return ResponseEntity.status(503).body(Map.of(
                "code", 503,
                "message", "Embedding 服务不可用，请联系管理员配置模型"
            ));
        }

        // 生成 embedding
        float[] embedding = embeddingService.embed(request.document());
        if (embedding == null || embedding.length == 0) {
            log.error("生成 Embedding 失败: id={}", id);
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "生成向量失败"
            ));
        }

        // 校验维度
        if (embedding.length != embeddingService.getDimensions()) {
            log.error("Embedding 维度不匹配: expected={}, actual={}",
                embeddingService.getDimensions(), embedding.length);
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "向量维度不匹配"
            ));
        }

        String docId = "kb-" + id;
        Map<String, String> metadata = new HashMap<>();
        metadata.put("knowledgeId", String.valueOf(id));
        metadata.put("title", request.title() != null ? request.title() : "");
        metadata.put("categoryKey", request.categoryKey() != null ? request.categoryKey() : "");
        metadata.put("enabled", String.valueOf(request.enabled() != null ? request.enabled() : true));

        boolean success = chromaClientService.updateDocument(docId, request.document(), embedding, metadata);

        if (success) {
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "向量更新成功",
                "data", Map.of("id", docId, "dimensions", embedding.length)
            ));
        } else {
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "向量更新失败"
            ));
        }
    }

    /**
     * 删除知识条目向量
     */
    @DeleteMapping("/knowledge/{id}")
    public ResponseEntity<Map<String, Object>> deleteKnowledge(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable("id") Long id) {

        if (!validateSecret(secret)) {
            return ResponseEntity.status(401).body(Map.of(
                "code", 401,
                "message", "认证失败"
            ));
        }

        String docId = "kb-" + id;
        boolean success = chromaClientService.deleteDocument(docId);

        if (success) {
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "向量删除成功"
            ));
        } else {
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "向量删除失败"
            ));
        }
    }

    @PostMapping("/repair-cases/{id}")
    public ResponseEntity<Map<String, Object>> upsertRepairCase(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable("id") Long id,
            @RequestBody RepairCaseSyncRequest request) {

        if (!validateSecret(secret)) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "认证失败"));
        }
        if (request == null || request.document() == null || request.document().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "历史案例内容不能为空"));
        }
        if (!embeddingService.isAvailable()) {
            return ResponseEntity.status(503).body(Map.of("code", 503, "message", "Embedding 服务不可用"));
        }
        if (!chromaClientService.isAvailable() || !chromaClientService.ensureCollection()) {
            return ResponseEntity.status(503).body(Map.of("code", 503, "message", "Chroma 服务不可用"));
        }

        float[] embedding = embeddingService.embed(request.document());
        if (embedding == null || embedding.length == 0 || embedding.length != embeddingService.getDimensions()) {
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "生成历史案例向量失败"));
        }

        String docId = "repair-case-" + id;
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "repair-case");
        metadata.put("ticketId", String.valueOf(id));
        metadata.put("categoryKey", safe(request.categoryKey()));
        metadata.put("categoryName", safe(request.categoryName()));
        metadata.put("failureCause", safe(request.failureCause()));
        metadata.put("repairMethod", safe(request.repairMethod()));
        metadata.put("materials", safe(request.materials()));
        metadata.put("result", safe(request.result()));

        boolean success = chromaClientService.updateDocument(docId, request.document(), embedding, metadata);
        if (success) {
            return ResponseEntity.ok(Map.of("code", 200, "message", "历史案例向量更新成功", "data", Map.of("id", docId)));
        }
        return ResponseEntity.status(500).body(Map.of("code", 500, "message", "历史案例向量更新失败"));
    }

    @DeleteMapping("/repair-cases/{id}")
    public ResponseEntity<Map<String, Object>> deleteRepairCase(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable("id") Long id) {

        if (!validateSecret(secret)) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "认证失败"));
        }

        boolean success = chromaClientService.deleteDocument("repair-case-" + id);
        if (success) {
            return ResponseEntity.ok(Map.of("code", 200, "message", "历史案例向量删除成功"));
        }
        return ResponseEntity.status(500).body(Map.of("code", 500, "message", "历史案例向量删除失败"));
    }

    @PostMapping("/repair-cases/search")
    public ResponseEntity<Map<String, Object>> searchRepairCases(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody RepairCaseSearchRequest request) {

        if (!validateSecret(secret)) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "认证失败"));
        }
        if (request == null || request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "检索内容不能为空"));
        }
        if (!embeddingService.isAvailable()) {
            return ResponseEntity.status(503).body(Map.of("code", 503, "message", "Embedding 服务不可用"));
        }
        if (!chromaClientService.isAvailable() || !chromaClientService.ensureCollection()) {
            return ResponseEntity.status(503).body(Map.of("code", 503, "message", "Chroma 服务不可用"));
        }

        float[] embedding = embeddingService.embed(request.query());
        if (embedding == null || embedding.length == 0 || embedding.length != embeddingService.getDimensions()) {
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "生成检索向量失败"));
        }

        Map<String, String> where = new HashMap<>();
        where.put("type", "repair-case");
        if (request.categoryKey() != null && !request.categoryKey().isBlank()) {
            where.put("categoryKey", request.categoryKey());
        }
        int limit = request.limit() == null ? 5 : Math.max(1, Math.min(request.limit(), 10));
        List<Map<String, Object>> results = chromaClientService.queryWithEmbedding(embedding, limit, where).stream()
            .map(item -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", item.id());
                row.put("similarity", item.similarity());
                row.put("metadata", item.metadata());
                return row;
            })
            .toList();

        return ResponseEntity.ok(Map.of("code", 200, "message", "历史案例检索成功", "data", results));
    }

    /**
     * 清空并重建索引
     */
    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildIndex(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {

        if (!validateSecret(secret)) {
            return ResponseEntity.status(401).body(Map.of(
                "code", 401,
                "message", "认证失败"
            ));
        }

        boolean success = chromaClientService.clearCollection();

        if (success) {
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "向量索引已清空，请重新同步知识条目"
            ));
        } else {
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "清空向量索引失败"
            ));
        }
    }

    /**
     * 验证内部密钥
     */
    private boolean validateSecret(String secret) {
        String effectiveSecret = (internalSecret != null && !internalSecret.isBlank())
            ? internalSecret
            : System.getenv("INTERNAL_SERVICE_SECRET");

        if (effectiveSecret == null || effectiveSecret.isBlank()) {
            log.error("INTERNAL_SERVICE_SECRET 未配置，拒绝内部请求");
            return false;
        }

        return effectiveSecret.equals(secret);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 知识同步请求
     */
    public record KnowledgeSyncRequest(
        String document,
        String title,
        String categoryKey,
        Boolean enabled
    ) {}

    public record RepairCaseSyncRequest(
        String document,
        String categoryKey,
        String categoryName,
        String failureCause,
        String repairMethod,
        String materials,
        String result
    ) {}

    public record RepairCaseSearchRequest(
        String query,
        String categoryKey,
        Integer limit
    ) {}
}
