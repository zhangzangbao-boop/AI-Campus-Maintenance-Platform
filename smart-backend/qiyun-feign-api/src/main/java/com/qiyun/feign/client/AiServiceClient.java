package com.qiyun.feign.client;

import com.qiyun.feign.dto.AnalyzeTicketRequest;
import com.qiyun.feign.dto.AnalyzeTicketResponse;
import com.qiyun.feign.dto.AnalyzeFeedbackSentimentRequest;
import com.qiyun.feign.dto.AnalyzeFeedbackSentimentResponse;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AI 服务 Feign 客户端
 */
@FeignClient(name = "qiyun-ai-service", contextId = "aiServiceClient", path = "/api/ai")
public interface AiServiceClient {

    /**
     * AI 分析工单
     * 返回包装后的响应，需要从 data 字段提取实际数据
     */
    @PostMapping("/analyze-ticket")
    Map<String, Object> analyzeTicket(@RequestBody AnalyzeTicketRequest request);

    @PostMapping("/analyze-feedback-sentiment")
    Map<String, Object> analyzeFeedbackSentiment(@RequestBody AnalyzeFeedbackSentimentRequest request);

    /**
     * 提取响应数据
     */
    default AnalyzeTicketResponse extractResponse(Map<String, Object> response) {
        if (response == null || response.get("data") == null) {
            return null;
        }
        Object data = response.get("data");
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            @SuppressWarnings("unchecked")
            java.util.List<String> keywords = (java.util.List<String>) dataMap.get("keywords");
            return new AnalyzeTicketResponse(
                (String) dataMap.get("category"),
                (String) dataMap.get("urgency"),
                (String) dataMap.get("suggestion"),
                keywords != null ? keywords : java.util.Collections.emptyList()
            );
        }
        return null;
    }

    default AnalyzeFeedbackSentimentResponse extractSentimentResponse(Map<String, Object> response) {
        if (response == null || response.get("data") == null) {
            return null;
        }
        Object data = response.get("data");
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            return new AnalyzeFeedbackSentimentResponse(
                dataMap.get("sentiment") == null ? null : String.valueOf(dataMap.get("sentiment")),
                toDouble(dataMap.get("score")),
                toStringList(dataMap.get("keywords")),
                dataMap.get("summary") == null ? null : String.valueOf(dataMap.get("summary"))
            );
        }
        return null;
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static java.util.List<String> toStringList(Object value) {
        if (value instanceof java.util.List<?> list) {
            return list.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return java.util.Arrays.stream(text.split("[,，、\\s]+"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
        }
        return java.util.Collections.emptyList();
    }
}
