package com.qiyun.feign.client;

import com.qiyun.feign.dto.AnalyzeTicketRequest;
import com.qiyun.feign.dto.AnalyzeTicketResponse;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AI 服务 Feign 客户端
 */
@FeignClient(name = "qiyun-ai-service", path = "/api/ai")
public interface AiServiceClient {

    /**
     * AI 分析工单
     * 返回包装后的响应，需要从 data 字段提取实际数据
     */
    @PostMapping("/analyze-ticket")
    Map<String, Object> analyzeTicket(@RequestBody AnalyzeTicketRequest request);

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
}