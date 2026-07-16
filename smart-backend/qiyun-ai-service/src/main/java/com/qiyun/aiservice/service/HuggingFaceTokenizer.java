package com.qiyun.aiservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class HuggingFaceTokenizer {

    private static final double UNKNOWN_SCORE = -100.0;

    private final Map<String, Integer> tokenIds;
    private final Map<String, Double> scores;
    private final int bosId;
    private final int eosId;
    private final int padId;
    private final int unkId;
    private final String replacement;
    private final boolean addPrefixSpace;

    private HuggingFaceTokenizer(
        Map<String, Integer> tokenIds,
        Map<String, Double> scores,
        int bosId,
        int eosId,
        int padId,
        int unkId,
        String replacement,
        boolean addPrefixSpace
    ) {
        this.tokenIds = tokenIds;
        this.scores = scores;
        this.bosId = bosId;
        this.eosId = eosId;
        this.padId = padId;
        this.unkId = unkId;
        this.replacement = replacement;
        this.addPrefixSpace = addPrefixSpace;
    }

    static HuggingFaceTokenizer fromFile(String tokenizerPath) throws IOException {
        Path path = Path.of(tokenizerPath);
        if (!Files.exists(path)) {
            throw new IOException("tokenizer.json not found: " + tokenizerPath);
        }

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(
            path.toFile(), new TypeReference<Map<String, Object>>() {}
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> model = (Map<String, Object>) root.get("model");
        if (model == null || !"Unigram".equals(String.valueOf(model.get("type")))) {
            throw new IOException("Only HuggingFace Unigram tokenizer.json is supported");
        }

        Map<String, Integer> tokenIds = new HashMap<>();
        Map<String, Double> scores = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<List<Object>> vocab = (List<List<Object>>) model.get("vocab");
        if (vocab == null || vocab.isEmpty()) {
            throw new IOException("tokenizer.json model.vocab is empty");
        }

        for (int i = 0; i < vocab.size(); i++) {
            List<Object> entry = vocab.get(i);
            if (entry.size() < 2) {
                continue;
            }
            String token = String.valueOf(entry.get(0));
            double score = entry.get(1) instanceof Number number
                ? number.doubleValue()
                : Double.parseDouble(String.valueOf(entry.get(1)));
            tokenIds.put(token, i);
            scores.put(token, score);
        }

        int bosId = tokenIds.getOrDefault("<s>", 0);
        int eosId = tokenIds.getOrDefault("</s>", 2);
        int padId = tokenIds.getOrDefault("<pad>", 1);
        int unkId = tokenIds.getOrDefault("<unk>", 3);

        String replacement = "\u2581";
        boolean addPrefixSpace = true;
        Object preTokenizerObj = root.get("pre_tokenizer");
        if (preTokenizerObj instanceof Map<?, ?> preTokenizer) {
            Object pretokenizersObj = preTokenizer.get("pretokenizers");
            if (pretokenizersObj instanceof List<?> pretokenizers) {
                for (Object item : pretokenizers) {
                    if (item instanceof Map<?, ?> itemMap
                        && "Metaspace".equals(String.valueOf(itemMap.get("type")))) {
                        Object configuredReplacement = itemMap.get("replacement");
                        if (configuredReplacement != null) {
                            replacement = String.valueOf(configuredReplacement);
                        }
                        Object configuredPrefix = itemMap.get("add_prefix_space");
                        if (configuredPrefix instanceof Boolean boolValue) {
                            addPrefixSpace = boolValue;
                        }
                    }
                }
            }
        }

        return new HuggingFaceTokenizer(
            tokenIds, scores, bosId, eosId, padId, unkId, replacement, addPrefixSpace
        );
    }

    TokenizationResult tokenize(List<String> texts, int maxSequenceLength) {
        List<List<Integer>> encoded = new ArrayList<>();
        int maxLength = 0;

        for (String text : texts) {
            List<Integer> ids = encode(text, maxSequenceLength);
            encoded.add(ids);
            maxLength = Math.max(maxLength, ids.size());
        }

        long[][] inputIds = new long[texts.size()][maxLength];
        long[][] attentionMask = new long[texts.size()][maxLength];
        long[][] tokenTypeIds = new long[texts.size()][maxLength];

        for (int row = 0; row < encoded.size(); row++) {
            Arrays.fill(inputIds[row], padId);
            List<Integer> ids = encoded.get(row);
            for (int col = 0; col < ids.size(); col++) {
                inputIds[row][col] = ids.get(col);
                attentionMask[row][col] = 1;
                tokenTypeIds[row][col] = 0;
            }
        }

        return new TokenizationResult(inputIds, attentionMask, tokenTypeIds);
    }

    private List<Integer> encode(String text, int maxSequenceLength) {
        List<Integer> ids = new ArrayList<>();
        ids.add(bosId);

        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.isEmpty()) {
            String[] parts = trimmed.split("\\s+");
            for (String part : parts) {
                if (part.isEmpty()) {
                    continue;
                }
                String piece = addPrefixSpace ? replacement + part : part;
                ids.addAll(encodePiece(piece));
            }
        }

        ids.add(eosId);
        if (maxSequenceLength > 0 && ids.size() > maxSequenceLength) {
            List<Integer> truncated = new ArrayList<>(ids.subList(0, maxSequenceLength));
            truncated.set(truncated.size() - 1, eosId);
            return truncated;
        }
        return ids;
    }

    private List<Integer> encodePiece(String piece) {
        int[] boundaries = codePointBoundaries(piece);
        int n = boundaries.length - 1;
        double[] best = new double[n + 1];
        int[] previous = new int[n + 1];
        String[] previousToken = new String[n + 1];
        Arrays.fill(best, Double.NEGATIVE_INFINITY);
        Arrays.fill(previous, -1);
        best[0] = 0.0;

        for (int start = 0; start < n; start++) {
            if (Double.isInfinite(best[start])) {
                continue;
            }

            for (int end = start + 1; end <= n; end++) {
                String token = piece.substring(boundaries[start], boundaries[end]);
                Double score = scores.get(token);
                if (score == null) {
                    continue;
                }
                double candidate = best[start] + score;
                if (candidate > best[end]) {
                    best[end] = candidate;
                    previous[end] = start;
                    previousToken[end] = token;
                }
            }

            if (previous[start + 1] < 0) {
                double candidate = best[start] + UNKNOWN_SCORE;
                if (candidate > best[start + 1]) {
                    best[start + 1] = candidate;
                    previous[start + 1] = start;
                    previousToken[start + 1] = "<unk>";
                }
            }
        }

        List<Integer> reversed = new ArrayList<>();
        for (int cursor = n; cursor > 0; cursor = previous[cursor]) {
            String token = previousToken[cursor];
            if (token == null || previous[cursor] < 0) {
                reversed.add(unkId);
                break;
            }
            reversed.add(tokenIds.getOrDefault(token, unkId));
        }

        List<Integer> ids = new ArrayList<>();
        for (int i = reversed.size() - 1; i >= 0; i--) {
            ids.add(reversed.get(i));
        }
        return ids;
    }

    private int[] codePointBoundaries(String text) {
        int codePointCount = text.codePointCount(0, text.length());
        int[] boundaries = new int[codePointCount + 1];
        int charIndex = 0;
        boundaries[0] = 0;
        for (int i = 1; i <= codePointCount; i++) {
            charIndex = text.offsetByCodePoints(charIndex, 1);
            boundaries[i] = charIndex;
        }
        return boundaries;
    }

    record TokenizationResult(
        long[][] inputIds,
        long[][] attentionMask,
        long[][] tokenTypeIds
    ) {}
}
