package com.qiyun.aiservice.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.qiyun.aiservice.config.EmbeddingConfig;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingConfig config;
    private final OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    private volatile boolean initialized = false;
    private volatile boolean initializationFailed = false;

    public EmbeddingService(EmbeddingConfig config) {
        this.config = config;
        this.env = OrtEnvironment.getEnvironment();

        if (config.isEnabled()) {
            initializeModel();
        }
    }

    private synchronized void initializeModel() {
        if (initialized || initializationFailed) {
            return;
        }

        try {
            String modelPath = getModelPath();
            if (modelPath == null || modelPath.isBlank()) {
                log.warn("Embedding model path is not configured; RAG embedding is disabled");
                initializationFailed = true;
                return;
            }

            Path model = Path.of(modelPath);
            if (!Files.exists(model) || Files.isDirectory(model)) {
                log.error("Embedding model file does not exist or is not a file: {}", modelPath);
                initializationFailed = true;
                return;
            }

            String tokenizerPath = getTokenizerPath();
            if (tokenizerPath == null || tokenizerPath.isBlank()) {
                log.warn("Embedding tokenizer path is not configured; RAG embedding is disabled");
                initializationFailed = true;
                return;
            }

            Path tokenizerFile = Path.of(tokenizerPath);
            if (!Files.exists(tokenizerFile) || Files.isDirectory(tokenizerFile)) {
                log.error("Embedding tokenizer file does not exist or is not a file: {}", tokenizerPath);
                initializationFailed = true;
                return;
            }

            tokenizer = HuggingFaceTokenizer.fromFile(tokenizerPath);

            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            session = env.createSession(modelPath, options);
            initialized = true;
            log.info(
                "Embedding model loaded: model={}, tokenizer={}, dimensions={}",
                modelPath, tokenizerPath, config.getDimensions()
            );
        } catch (Exception e) {
            log.error("Failed to load embedding model/tokenizer: {}", e.getMessage(), e);
            initializationFailed = true;
        }
    }

    private String getModelPath() {
        String envPath = System.getenv("EMBEDDING_MODEL_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return envPath;
        }
        return config.getModelPath();
    }

    private String getTokenizerPath() {
        String envPath = System.getenv("EMBEDDING_TOKENIZER_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return envPath;
        }
        return config.getTokenizerPath();
    }

    public boolean isAvailable() {
        if (!config.isEnabled()) {
            return false;
        }
        if (initializationFailed) {
            return false;
        }
        if (!initialized) {
            initializeModel();
        }
        return initialized && session != null && tokenizer != null;
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        List<float[]> results = embedBatch(Collections.singletonList(text));
        return results.isEmpty() ? null : results.get(0);
    }

    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        if (!isAvailable()) {
            log.warn("Embedding service is unavailable, using deterministic local fallback embeddings");
            return texts.stream()
                .map(this::fallbackEmbed)
                .toList();
        }

        try {
            List<float[]> embeddings = new ArrayList<>();
            int batchSize = Math.max(1, config.getBatchSize());

            for (int i = 0; i < texts.size(); i += batchSize) {
                int end = Math.min(i + batchSize, texts.size());
                List<String> batch = texts.subList(i, end);
                embeddings.addAll(processBatch(batch));
            }

            return embeddings;
        } catch (Exception e) {
            log.error("Failed to generate embeddings: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private float[] fallbackEmbed(String text) {
        int dimensions = config.getDimensions() > 0 ? config.getDimensions() : 384;
        float[] vector = new float[dimensions];
        String normalized = text == null ? "" : text.trim().toLowerCase();

        if (normalized.isBlank()) {
            return vector;
        }

        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            addToken(vector, String.valueOf(current), 1.0f);
            if (i + 1 < normalized.length()) {
                addToken(vector, normalized.substring(i, i + 2), 1.35f);
            }
            if (i + 2 < normalized.length()) {
                addToken(vector, normalized.substring(i, i + 3), 1.15f);
            }
        }

        normalize(vector);
        return vector;
    }

    private void addToken(float[] vector, String token, float weight) {
        int index = Math.floorMod(token.hashCode(), vector.length);
        vector[index] += weight;
    }

    private List<float[]> processBatch(List<String> texts) throws OrtException {
        HuggingFaceTokenizer.TokenizationResult tokenization =
            tokenizer.tokenize(texts, config.getMaxSequenceLength());

        try (OnnxTensor inputIds = createTensor(tokenization.inputIds());
             OnnxTensor attentionMask = createTensor(tokenization.attentionMask());
             OnnxTensor tokenTypeIds = createTensor(tokenization.tokenTypeIds())) {

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIds);
            inputs.put("attention_mask", attentionMask);
            inputs.put("token_type_ids", tokenTypeIds);

            try (OrtSession.Result result = session.run(inputs)) {
                float[][][] output = (float[][][]) result.get(0).getValue();

                List<float[]> embeddings = new ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                    float[] embedding = meanPool(output[i], tokenization.attentionMask()[i]);
                    normalize(embedding);
                    embeddings.add(embedding);
                }
                return embeddings;
            }
        }
    }

    private OnnxTensor createTensor(long[][] data) throws OrtException {
        long[] flatData = new long[data.length * data[0].length];
        int idx = 0;
        for (long[] row : data) {
            for (long val : row) {
                flatData[idx++] = val;
            }
        }

        LongBuffer buffer = LongBuffer.wrap(flatData);
        return OnnxTensor.createTensor(env, buffer, new long[]{data.length, data[0].length});
    }

    private float[] meanPool(float[][] hiddenStates, long[] attentionMask) {
        int seqLen = hiddenStates.length;
        int hiddenSize = hiddenStates[0].length;
        float[] pooled = new float[hiddenSize];
        float maskSum = 0;

        for (int i = 0; i < seqLen; i++) {
            if (attentionMask[i] == 1) {
                for (int j = 0; j < hiddenSize; j++) {
                    pooled[j] += hiddenStates[i][j];
                }
                maskSum += 1;
            }
        }

        if (maskSum > 0) {
            for (int i = 0; i < hiddenSize; i++) {
                pooled[i] /= maskSum;
            }
        }

        return pooled;
    }

    private void normalize(float[] vector) {
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    public int getDimensions() {
        return config.getDimensions();
    }

    public void close() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                log.warn("Failed to close embedding session: {}", e.getMessage());
            }
        }
    }
}
