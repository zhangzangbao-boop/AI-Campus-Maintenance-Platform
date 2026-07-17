package com.qiyun.repairservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.feign.client.AiServiceClient;
import com.qiyun.feign.dto.AnalyzeFeedbackSentimentRequest;
import com.qiyun.feign.dto.AnalyzeFeedbackSentimentResponse;
import com.qiyun.repairservice.domain.entity.Rating;
import com.qiyun.repairservice.repository.RatingRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackSentimentAnalysisService {

    private final RatingRepository ratingRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void analyzeAsync(Long ratingId) {
        try {
            analyzeNow(ratingId);
        } catch (Exception e) {
            log.warn("Feedback sentiment analysis skipped: ratingId={}, error={}", ratingId, e.getMessage());
        }
    }

    @Transactional
    public AnalyzeFeedbackSentimentResponse analyzeNow(Long ratingId) {
        Rating rating = ratingRepository.findById(ratingId)
            .orElseThrow(() -> new IllegalArgumentException("Rating not found: " + ratingId));
        String comment = rating.getComment() == null ? "" : rating.getComment().trim();

        AnalyzeFeedbackSentimentResponse response = tryAi(comment);
        if (response == null) {
            response = analyzeByRules(comment);
        }

        rating.setSentiment(normalizeSentiment(response.sentiment(), comment));
        rating.setSentimentScore(clamp(response.score() == null ? ruleScore(rating.getSentiment(), comment) : response.score()));
        rating.setSentimentKeywords(toJson(nonEmptyKeywords(response.keywords(), comment)));
        rating.setSentimentSummary(firstText(response.summary(), buildSummary(rating.getSentiment())));
        rating.setSentimentAnalyzedAt(LocalDateTime.now());
        ratingRepository.save(rating);
        return response;
    }

    private AnalyzeFeedbackSentimentResponse tryAi(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> response = aiServiceClient.analyzeFeedbackSentiment(
                new AnalyzeFeedbackSentimentRequest(comment)
            );
            return aiServiceClient.extractSentimentResponse(response);
        } catch (Exception e) {
            log.warn("AI feedback sentiment unavailable, fallback to local rules: {}", e.getMessage());
            return null;
        }
    }

    private AnalyzeFeedbackSentimentResponse analyzeByRules(String comment) {
        String sentiment = normalizeSentiment(null, comment);
        return new AnalyzeFeedbackSentimentResponse(
            sentiment,
            ruleScore(sentiment, comment),
            extractKeywords(comment),
            buildSummary(sentiment)
        );
    }

    private String normalizeSentiment(String sentiment, String comment) {
        if (sentiment != null) {
            String normalized = sentiment.trim().toUpperCase(Locale.ROOT);
            if ("POSITIVE".equals(normalized) || "NEUTRAL".equals(normalized) || "NEGATIVE".equals(normalized)) {
                return normalized;
            }
        }
        String text = comment == null ? "" : comment.toLowerCase(Locale.ROOT);
        int positive = countContains(text, "\u6ee1\u610f", "\u53ca\u65f6", "\u5f88\u5feb", "\u4e13\u4e1a", "\u8d1f\u8d23", "\u8010\u5fc3", "\u89e3\u51b3", "\u5e72\u51c0", "\u4e0d\u9519", "\u5f88\u597d", "\u597d\u8bc4", "\u611f\u8c22");
        int negative = countContains(text, "\u4e0d\u6ee1\u610f", "\u592a\u6162", "\u5f88\u6162", "\u62d6\u5ef6", "\u6577\u884d", "\u6ca1\u89e3\u51b3", "\u672a\u89e3\u51b3", "\u5dee", "\u6295\u8bc9", "\u6001\u5ea6\u4e0d\u597d", "\u7cdf\u7cd5", "\u4ecd\u7136", "\u518d\u6b21\u574f");
        if (negative > positive) {
            return "NEGATIVE";
        }
        if (positive > negative) {
            return "POSITIVE";
        }
        return "NEUTRAL";
    }

    private double ruleScore(String sentiment, String comment) {
        int strong = countContains(comment == null ? "" : comment, "\u975e\u5e38", "\u7279\u522b", "\u5f88", "\u592a", "\u4e25\u91cd", "\u5b8c\u5168", "\u4e00\u76f4");
        double base = switch (sentiment) {
            case "POSITIVE" -> 0.78;
            case "NEGATIVE" -> 0.82;
            default -> 0.55;
        };
        return clamp(base + Math.min(strong, 2) * 0.08);
    }

    private List<String> nonEmptyKeywords(List<String> keywords, String comment) {
        if (keywords != null && !keywords.isEmpty()) {
            return keywords.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .limit(5)
                .toList();
        }
        return extractKeywords(comment);
    }

    private List<String> extractKeywords(String comment) {
        List<String> keywords = new ArrayList<>();
        String text = comment == null ? "" : comment;
        addKeywordIfPresent(keywords, text, "及时", "快", "很快", "及时");
        addKeywordIfPresent(keywords, text, "态度", "态度", "耐心", "负责", "敷衍");
        addKeywordIfPresent(keywords, text, "质量", "专业", "干净", "质量", "返修");
        addKeywordIfPresent(keywords, text, "未解决", "没解决", "未解决", "仍然", "再次坏");
        addKeywordIfPresent(keywords, text, "等待", "等", "慢", "拖延");
        addKeywordIfPresent(keywords, text, "满意", "满意", "好评", "感谢", "不错");
        if (keywords.isEmpty()) {
            keywords.add("服务反馈");
        }
        return keywords.stream().distinct().limit(5).toList();
    }

    private void addKeywordIfPresent(List<String> keywords, String text, String label, String... matches) {
        for (String match : matches) {
            if (text.contains(match)) {
                keywords.add(label);
                return;
            }
        }
    }

    private String buildSummary(String sentiment) {
        return switch (sentiment) {
            case "POSITIVE" -> "学生反馈整体满意，维修服务体验较好。";
            case "NEGATIVE" -> "学生反馈存在不满，建议管理员跟进处理质量和沟通情况。";
            default -> "学生反馈较为中性，可作为常规服务记录参考。";
        };
    }

    private int countContains(String text, String... words) {
        int count = 0;
        for (String word : words) {
            if (text.contains(word.toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, Math.round(value * 100.0) / 100.0));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
