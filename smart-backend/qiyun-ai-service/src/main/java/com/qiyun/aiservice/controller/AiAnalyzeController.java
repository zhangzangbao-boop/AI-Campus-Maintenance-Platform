package com.qiyun.aiservice.controller;

import com.qiyun.aiservice.dto.AnalyzeTicketRequest;
import com.qiyun.aiservice.dto.AnalyzeTicketResponse;
import com.qiyun.aiservice.service.AiAnalyzeService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 智能分析接口控制器
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAnalyzeController {

    private final AiAnalyzeService aiAnalyzeService;

    /**
     * AI 分析工单接口
     * POST /api/ai/analyze-ticket
     */
    @PostMapping("/analyze-ticket")
    public ResponseEntity<Map<String, Object>> analyzeTicket(
            @Valid @RequestBody AnalyzeTicketRequest request) {

        AnalyzeTicketResponse response = aiAnalyzeService.analyze(request);

        // 统一响应格式
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "AI 分析完成");
        result.put("data", response);

        return ResponseEntity.ok(result);
    }
}