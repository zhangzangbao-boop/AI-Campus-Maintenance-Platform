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
 * Chroma 鍚戦噺鏁版嵁搴撳鎴风鏈嶅姟
 * 璐熻矗涓?Chroma API 閫氫俊
 */
@Slf4j
@Service
public class ChromaClientService {

    private final ChromaConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private String collectionId;
    private String collectionApiBase;

    public ChromaClientService(ChromaConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .build();
    }

    /**
     * 妫€鏌?Chroma 鏈嶅姟鏄惁鍙敤
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
            log.debug("Chroma 鏈嶅姟涓嶅彲鐢? {}", e.getMessage());
            return false;
        }
    }

    /**
     * 鍒濆鍖栭泦鍚堬紙濡傛灉涓嶅瓨鍦ㄥ垯鍒涘缓锛?
     */

    public Map<String, Object> diagnosticStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("codeMarker", "chroma-v2-get-or-create-20260721-3");
        status.put("url", normalizeUrl(config.getUrl()));
        status.put("collection", config.getCollection());
        status.put("tenant", safeTenant());
        status.put("database", safeDatabase());
        status.put("available", isAvailable());
        boolean initialized = ensureCollection();
        status.put("initialized", initialized);
        status.put("collectionId", collectionId);
        status.put("collectionApiBase", collectionApiBase);
        status.put("count", initialized ? countDocuments() : -1);
        return status;
    }

    public int countDocuments() {
        if (collectionId == null && !ensureCollection()) {
            return -1;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(collectionEndpoint("/count")))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Chroma count failed: status={}, body={}", response.statusCode(), response.body());
                return -1;
            }
            return Integer.parseInt(response.body().trim());
        } catch (Exception e) {
            log.warn("Chroma count exception: {}", e.getMessage());
            return -1;
        }
    }

    public boolean ensureCollection() {
        try {
            String url = normalizeUrl(config.getUrl());
            String collectionName = config.getCollection();

            if (ensureCollectionV2(url, collectionName)) {
                return true;
            }

            // 妫€鏌ラ泦鍚堟槸鍚﹀瓨鍦?
            HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(collectionDeleteEndpoint(url, collectionName)))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .GET()
                .build();

            HttpResponse<String> getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());

            if (getResponse.statusCode() == 200) {
                // 闆嗗悎宸插瓨鍦?
                Map<String, Object> responseMap = objectMapper.readValue(
                    getResponse.body(), new TypeReference<Map<String, Object>>() {}
                );
                this.collectionId = String.valueOf(responseMap.get("id"));
                this.collectionApiBase = url + "/api/v1/collections";
                log.info("Chroma 闆嗗悎宸插瓨鍦? {} (id={})", collectionName, collectionId);
                return true;
            }

            // 闆嗗悎涓嶅瓨鍦紝鍒涘缓鏂伴泦鍚?
            Map<String, Object> createBody = new HashMap<>();
            createBody.put("name", collectionName);
            // 浣跨敤榛樿鐨?all-MiniLM-L6-v2 embedding 妯″瀷
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
                this.collectionId = String.valueOf(responseMap.get("id"));
                this.collectionApiBase = url + "/api/v1/collections";
                log.info("Chroma 闆嗗悎鍒涘缓鎴愬姛: {} (id={})", collectionName, collectionId);
                return true;
            } else {
                log.error("鍒涘缓 Chroma 闆嗗悎澶辫触: status={}", createResponse.statusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("鍒濆鍖?Chroma 闆嗗悎寮傚父: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 娣诲姞鏂囨。鍒伴泦鍚堬紙浣跨敤棰勮绠楃殑 embedding锛?
     *
     * @param id        鏂囨。鍞竴鏍囪瘑
     * @param document  鏂囨。鍐呭
     * @param embedding 鏂囨。鍚戦噺
     * @param metadata  鍏冩暟鎹?
     * @return 鏄惁鎴愬姛
     */

    private boolean ensureCollectionV2(String url, String collectionName) {
        try {
            String apiBase = url + "/api/v2/tenants/" + safeTenant()
                + "/databases/" + safeDatabase() + "/collections";
            Map<String, Object> body = buildCreateCollectionBody(collectionName);
            body.put("get_or_create", true);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                Map<String, Object> responseMap = objectMapper.readValue(
                    response.body(), new TypeReference<Map<String, Object>>() {}
                );
                this.collectionId = String.valueOf(responseMap.get("id"));
                this.collectionApiBase = apiBase;
                log.info("Chroma v2 ?????: {} (id={})", collectionName, collectionId);
                return true;
            }
            log.warn("Chroma v2 ??????????? v1: status={}, body={}",
                response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.warn("Chroma v2 ??????????? v1: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildCreateCollectionBody(String collectionName) {
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("name", collectionName);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("description", "Campus maintenance knowledge base");
        createBody.put("metadata", metadata);
        return createBody;
    }

    public boolean addDocument(String id, String document, float[] embedding, Map<String, String> metadata) {
        if (collectionId == null) {
            if (!ensureCollection()) {
                return false;
            }
        }

        // 鏍￠獙 embedding
        if (embedding == null || embedding.length == 0) {
            log.error("Embedding 涓虹┖锛屾棤娉曟坊鍔犳枃妗? id={}", id);
            return false;
        }

        try {
            String url = normalizeUrl(config.getUrl());

            Map<String, Object> body = new HashMap<>();
            body.put("ids", List.of(id));
            body.put("documents", List.of(document));
            body.put("embeddings", List.of(toBoxedArray(embedding)));
            if (metadata != null && !metadata.isEmpty()) {
                body.put("metadatas", List.of(metadata));
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(collectionEndpoint("/add")))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 201;
        } catch (Exception e) {
            log.error("娣诲姞鏂囨。鍒?Chroma 澶辫触: id={}, error={}", id, e.getMessage());
            return false;
        }
    }

    /**
     * 娣诲姞鏂囨。鍒伴泦鍚堬紙鍏煎鏃ф帴鍙ｏ紝涓嶆帹鑽愪娇鐢級
     *
     * @deprecated Chroma 0.5.20+ 闇€瑕佹彁渚?embedding锛岃浣跨敤 {@link #addDocument(String, String, float[], Map)}
     */
    @Deprecated
    public boolean addDocument(String id, String document, Map<String, String> metadata) {
        log.warn("浣跨敤宸插簾寮冪殑 addDocument 鏂规硶锛孋hroma 0.5.20+ 闇€瑕佹彁渚?embedding");
        return false;
    }

    /**
     * 鏇存柊闆嗗悎涓殑鏂囨。锛堜娇鐢ㄩ璁＄畻鐨?embedding锛?
     *
     * @param id        鏂囨。鍞竴鏍囪瘑
     * @param document  鏂版枃妗ｅ唴瀹?
     * @param embedding 鏂囨。鍚戦噺
     * @param metadata  鏂板厓鏁版嵁
     * @return 鏄惁鎴愬姛
     */
    public boolean updateDocument(String id, String document, float[] embedding, Map<String, String> metadata) {
        if (collectionId == null) {
            if (!ensureCollection()) {
                return false;
            }
        }

        // 鏍￠獙 embedding
        if (embedding == null || embedding.length == 0) {
            log.error("Embedding 涓虹┖锛屾棤娉曟洿鏂版枃妗? id={}", id);
            return false;
        }

        try {
            String url = normalizeUrl(config.getUrl());

            Map<String, Object> body = new HashMap<>();
            body.put("ids", List.of(id));
            body.put("documents", List.of(document));
            body.put("embeddings", List.of(toBoxedArray(embedding)));
            if (metadata != null && !metadata.isEmpty()) {
                body.put("metadatas", List.of(metadata));
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(collectionEndpoint("/upsert")))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return true;
            }
            log.warn("Chroma upsert failed: id={}, status={}, body={}", id, response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.error("鏇存柊 Chroma 鏂囨。澶辫触: id={}, error={}", id, e.getMessage());
            return false;
        }
    }

    /**
     * 鏇存柊闆嗗悎涓殑鏂囨。锛堝吋瀹规棫鎺ュ彛锛屼笉鎺ㄨ崘浣跨敤锛?
     *
     * @deprecated Chroma 0.5.20+ 闇€瑕佹彁渚?embedding锛岃浣跨敤 {@link #updateDocument(String, String, float[], Map)}
     */
    @Deprecated
    public boolean updateDocument(String id, String document, Map<String, String> metadata) {
        log.warn("浣跨敤宸插簾寮冪殑 updateDocument 鏂规硶锛孋hroma 0.5.20+ 闇€瑕佹彁渚?embedding");
        return false;
    }

    /**
     * 浠庨泦鍚堜腑鍒犻櫎鏂囨。
     *
     * @param id 鏂囨。鍞竴鏍囪瘑
     * @return 鏄惁鎴愬姛
     */
    public boolean deleteDocument(String id) {
        if (collectionId == null) {
            return true; // 闆嗗悎涓嶅瓨鍦紝鏃犻渶鍒犻櫎
        }

        try {
            String url = normalizeUrl(config.getUrl());

            Map<String, Object> body = new HashMap<>();
            body.put("ids", List.of(id));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(collectionEndpoint("/delete")))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("鍒犻櫎 Chroma 鏂囨。澶辫触: id={}, error={}", id, e.getMessage());
            return false;
        }
    }

    /**
     * 妫€绱㈢浉浼兼枃妗ｏ紙浣跨敤棰勮绠楃殑 embedding锛?
     *
     * @param queryEmbedding 鏌ヨ鍚戦噺
     * @param topK           杩斿洖鏁伴噺
     * @param whereFilter    鍏冩暟鎹繃婊ゆ潯浠?
     * @return 妫€绱㈢粨鏋滃垪琛?
     */
    public List<RetrievalResult> queryWithEmbedding(float[] queryEmbedding, int topK, Map<String, String> whereFilter) {
        if (collectionId == null) {
            if (!ensureCollection()) {
                return List.of();
            }
        }

        // 鏍￠獙 embedding
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            log.error("Query embedding is empty, cannot search Chroma");
            return List.of();
        }

        try {
            String url = normalizeUrl(config.getUrl());

            Map<String, Object> body = new HashMap<>();
            body.put("query_embeddings", List.of(toBoxedArray(queryEmbedding)));
            body.put("n_results", topK > 0 ? topK : config.getTopK());
            if (whereFilter != null && !whereFilter.isEmpty()) {
                body.put("where", whereFilter);
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(collectionEndpoint("/query")))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Chroma 鏌ヨ澶辫触: status={}", response.statusCode());
                return List.of();
            }

            return parseQueryResponse(response.body());
        } catch (Exception e) {
            log.error("Chroma 鏌ヨ寮傚父: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 妫€绱㈢浉浼兼枃妗ｏ紙鍏煎鏃ф帴鍙ｏ紝涓嶆帹鑽愪娇鐢級
     *
     * @deprecated Chroma 0.5.20+ 闇€瑕佹彁渚?embedding锛岃浣跨敤 {@link #queryWithEmbedding(float[], int, Map)}
     */
    @Deprecated
    public List<RetrievalResult> query(String query, int topK, Map<String, String> whereFilter) {
        log.warn("浣跨敤宸插簾寮冪殑 query 鏂规硶锛孋hroma 0.5.20+ 闇€瑕佹彁渚?embedding");
        return List.of();
    }

    /**
     * 鍒犻櫎闆嗗悎涓殑鎵€鏈夋枃妗ｏ紙鐢ㄤ簬閲嶅缓绱㈠紩锛?
     *
     * @return 鏄惁鎴愬姛
     */
    public boolean clearCollection() {
        try {
            String url = normalizeUrl(config.getUrl());
            String collectionName = config.getCollection();
            ensureCollection();

            // 鍒犻櫎鏁翠釜闆嗗悎
            HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(collectionDeleteEndpoint(url, collectionName)))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .DELETE()
                .build();

            httpClient.send(deleteRequest, HttpResponse.BodyHandlers.ofString());

            // 閲嶇疆 collectionId锛屼笅娆℃搷浣滄椂浼氶噸鏂板垱寤?
            collectionId = null;
            collectionApiBase = null;
            log.info("Chroma 闆嗗悎宸叉竻绌? {}", collectionName);
            return true;
        } catch (Exception e) {
            log.error("娓呯┖ Chroma 闆嗗悎澶辫触: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 瑙ｆ瀽鏌ヨ鍝嶅簲
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

            // Chroma 杩斿洖宓屽鏁扮粍锛屽彇绗竴涓紙鍥犱负鎴戜滑鍙煡璇簡涓€涓枃鏈級
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

                // Chroma 浣跨敤璺濈锛岃浆鎹负鐩镐技搴︼紙璺濈瓒婂皬瓒婄浉浼硷級
                // 鍋囪璺濈鑼冨洿鏄?0-2锛岃浆鎹负鐩镐技搴?1 - distance/2
                double similarity = Math.max(0, Math.min(1, 1 - distance / 2));

                results.add(new RetrievalResult(id, document, metadata, similarity));
            }
        } catch (Exception e) {
            log.error("瑙ｆ瀽 Chroma 鏌ヨ鍝嶅簲澶辫触: {}", e.getMessage());
        }

        return results;
    }

    /**
     * 鏍囧噯鍖?URL
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
     * 灏?float[] 杞崲涓?Float[]锛堢敤浜?JSON 搴忓垪鍖栵級
     */

    private String collectionEndpoint(String operation) {
        if (collectionApiBase == null || collectionId == null) {
            throw new IllegalStateException("Chroma collection is not initialized");
        }
        return collectionApiBase + "/" + collectionId + operation;
    }

    private String collectionDeleteEndpoint(String url, String collectionName) {
        if (collectionApiBase != null && collectionApiBase.contains("/api/v2/") && collectionId != null) {
            return collectionApiBase + "/" + collectionId;
        }
        return url + "/api/v1/collections/" + collectionName;
    }

    private String safeTenant() {
        String tenant = config.getTenant();
        return tenant == null || tenant.isBlank() ? "default_tenant" : tenant.trim();
    }

    private String safeDatabase() {
        String database = config.getDatabase();
        return database == null || database.isBlank() ? "default_database" : database.trim();
    }

    private Float[] toBoxedArray(float[] arr) {
        Float[] boxed = new Float[arr.length];
        for (int i = 0; i < arr.length; i++) {
            boxed[i] = arr[i];
        }
        return boxed;
    }

    /**
     * 妫€绱㈢粨鏋滆褰?
     */
    public record RetrievalResult(
        String id,
        String document,
        Map<String, String> metadata,
        double similarity
    ) {}
}
