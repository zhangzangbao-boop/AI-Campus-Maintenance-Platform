package com.qiyun.aiservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.aiservice.config.ChromaConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Chroma 向量数据库客户端服务
 * 负责与 Chroma API 通信
 */
@Slf4j
@Service
public class ChromaClientService {

    private final ChromaConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private String collectionId;

    public ChromaClientService(ChromaConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .build();
    }

    /**
     * 检查 Chroma 服务是否可用
     */
    public boolean isAvailable() {
        try {
            String url = normalizeUrl(config.getUrl());
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/v1/heartbeat"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Chroma 服务不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 初始化集合（如果不存在则创建）
     */
    public boolean ensureCollection() {
        try {
            String url = normalizeUrl(config.getUrl());
            String collectionName = config.getCollection();

            // 检查集合是否存在
            HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/v1/collections/" + collectionName))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .GET()
                .build();

            HttpResponse<String> getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());

            if (getResponse.statusCode() == 200) {
                // 集合已存在
                Map<String, Object> responseMap = objectMapper.readValue(
                    getResponse.body(), new TypeReference<Map<String, Object>>() {}
                );
                this.collectionId = (String) responseMap.get("id");
                log.info("Chroma 集合已存在: {} (id={})", collectionName, collectionId);
                return true;
            }

            // 集合不存在，创建新集合
            Map<String, Object> createBody = new HashMap<>();
            createBody.put("name", collectionName);
            // 使用默认的 all-MiniLM-L6-v2 embedding 模型
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("description", "Campus maintenance knowledge base");
            createBody.put("metadata", metadata);

            HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/v1/collections"))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(createBody)))
                .build();

            HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());

            if (createResponse.statusCode() == 200 || createResponse.statusCode() == 201) {
                Map<String, Object> responseMap = objectMapper.readValue(
                    createResponse.body(), new TypeReference<Map<String, Object>>() {}
                );
                this.collectionId = (String) responseMap.get("id");
                log.info("Chroma 集合创建成功: {} (id={})", collectionName, collectionId);
                return true;
            } else {
                log.error("创建 Chroma 集合失败: status={}", createResponse.statusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("初始化 Chroma 集合异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 添加文档到集合
     *
     * @param id       文档唯一标识
     * @param document 文档内容
     * @param metadata 元数据
     * @return 是否成功
     */
    public boolean addDocument(String id, String document, Map<String, String> metadata) {
        if (collectionId == null) {
            if (!ensureCollection()) {
                return false;
            }
        }

        try {
            String url = normalizeUrl(config.getUrl());

            Map<String, Object> body = new HashMap<>();
            body.put("ids", List.of(id));
            body.put("documents", List.of(document));
            if (metadata != null && !metadata.isEmpty()) {
                body.put("metadatas", List.of(metadata));
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/v1/collections/" + collectionId + "/add"))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 201;
        } catch (Exception e) {
            log.error("添加文档到 Chroma 失败: id={}, error={}", id, e.getMessage());
            return false;
        }
    }

    /**
     * 更新集合中的文档
     *
     * @param id       文档唯一标识
     * @param document 新文档内容
     * @param metadata 新元数据
     * @return 是否成功
     */
    public boolean updateDocument(String id, String document, Map<String, String> metadata) {
        if (collectionId == null) {
            if (!ensureCollection()) {
                return false;
            }
        }

        try {
            String url = normalizeUrl(config.getUrl());

            Map<String, Object> body = new HashMap<>();
            body.put("ids", List.of(id));
            body.put("documents", List.of(document));
            if (metadata != null && !metadata.isEmpty()) {
                body.put("metadatas", List.of(metadata));
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/v1/collections/" + collectionId + "/update"))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("更新 Chroma 文档失败: id={}, error={}", id, e.getMessage());
            return false;
        }
    }

    /**
     * 从集合中删除文档
     *
     * @param id 文档唯一标识
     * @return 是否成功
     */
    public boolean deleteDocument(String id) {
        if (collectionId == null) {
            return true; // 集合不存在，无需删除
        }

        try {
            String url = normalizeUrl(config.getUrl());

            Map<String, Object> body = new HashMap<>();
            body.put("ids", List.of(id));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/v1/collections/" + collectionId + "/delete"))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("删除 Chroma 文档失败: id={}, error={}", id, e.getMessage());
            return false;
        }
    }

    /**
     * 检索相似文档
     *
     * @param query      查询文本
     * @param topK       返回数量
     * @param whereFilter 元数据过滤条件
     * @return 检索结果列表
     */
    public List<RetrievalResult> query(String query, int topK, Map<String, String> whereFilter) {
        if (collectionId == null) {
            if (!ensureCollection()) {
                return List.of();
            }
        }

        try {
            String url = normalizeUrl(config.getUrl());

            Map<String, Object> body = new HashMap<>();
            body.put("query_texts", List.of(query));
            body.put("n_results", topK > 0 ? topK : config.getTopK());
            if (whereFilter != null && !whereFilter.isEmpty()) {
                body.put("where", whereFilter);
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/v1/collections/" + collectionId + "/query"))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Chroma 查询失败: status={}", response.statusCode());
                return List.of();
            }

            return parseQueryResponse(response.body());
        } catch (Exception e) {
            log.error("Chroma 查询异常: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 删除集合中的所有文档（用于重建索引）
     *
     * @return 是否成功
     */
    public boolean clearCollection() {
        try {
            String url = normalizeUrl(config.getUrl());
            String collectionName = config.getCollection();

            // 删除整个集合
            HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/v1/collections/" + collectionName))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .DELETE()
                .build();

            httpClient.send(deleteRequest, HttpResponse.BodyHandlers.ofString());

            // 重置 collectionId，下次操作时会重新创建
            collectionId = null;
            log.info("Chroma 集合已清空: {}", collectionName);
            return true;
        } catch (Exception e) {
            log.error("清空 Chroma 集合失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析查询响应
     */
    private List<RetrievalResult> parseQueryResponse(String responseBody) {
        List<RetrievalResult> results = new ArrayList<>();

        try {
            Map<String, Object> responseMap = objectMapper.readValue(
                responseBody, new TypeReference<Map<String, Object>>() {}
            );

            Object idsObj = responseMap.get("ids");
            Object documentsObj = responseMap.get("documents");
            Object metadatasObj = responseMap.get("metadatas");
            Object distancesObj = responseMap.get("distances");

            if (!(idsObj instanceof List<?> idsList) || idsList.isEmpty()) {
                return results;
            }

            // Chroma 返回嵌套数组，取第一个（因为我们只查询了一个文本）
            List<?> ids = (List<?>) idsList.get(0);
            List<?> documents = documentsObj instanceof List<?> list && !list.isEmpty()
                ? (List<?>) list.get(0) : List.of();
            List<?> metadatas = metadatasObj instanceof List<?> list && !list.isEmpty()
                ? (List<?>) list.get(0) : List.of();
            List<?> distances = distancesObj instanceof List<?> list && !list.isEmpty()
                ? (List<?>) list.get(0) : List.of();

            for (int i = 0; i < ids.size(); i++) {
                String id = String.valueOf(ids.get(i));
                String document = i < documents.size() ? String.valueOf(documents.get(i)) : "";
                @SuppressWarnings("unchecked")
                Map<String, String> metadata = i < metadatas.size() && metadatas.get(i) instanceof Map
                    ? (Map<String, String>) metadatas.get(i) : Map.of();
                double distance = i < distances.size() && distances.get(i) instanceof Number
                    ? ((Number) distances.get(i)).doubleValue() : 1.0;

                // Chroma 使用距离，转换为相似度（距离越小越相似）
                // 假设距离范围是 0-2，转换为相似度 1 - distance/2
                double similarity = Math.max(0, Math.min(1, 1 - distance / 2));

                results.add(new RetrievalResult(id, document, metadata, similarity));
            }
        } catch (Exception e) {
            log.error("解析 Chroma 查询响应失败: {}", e.getMessage());
        }

        return results;
    }

    /**
     * 标准化 URL
     */
    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8000";
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * 检索结果记录
     */
    public record RetrievalResult(
        String id,
        String document,
        Map<String, String> metadata,
        double similarity
    ) {}
}