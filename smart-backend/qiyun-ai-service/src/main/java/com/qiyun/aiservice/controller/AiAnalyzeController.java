package com.qiyun.aiservice.controller;

import com.qiyun.aiservice.config.DeepSeekConfig;
import com.qiyun.aiservice.dto.AnalyzeFeedbackSentimentRequest;
import com.qiyun.aiservice.dto.AnalyzeFeedbackSentimentResponse;
import com.qiyun.aiservice.dto.AnalyzeTicketRequest;
import com.qiyun.aiservice.dto.AnalyzeTicketResponse;
import com.qiyun.aiservice.service.AiAnalyzeService;
import com.qiyun.aiservice.service.DeepSeekClientService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final DeepSeekClientService deepSeekClientService;
    private final DeepSeekConfig deepSeekConfig;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> data = new HashMap<>();
        data.put("aiEnabled", deepSeekConfig.isEnabled());
        data.put("aiConfigured", deepSeekClientService.isConfigured());
        data.put("aiProvider", deepSeekConfig.getProvider());
        data.put("aiModel", deepSeekConfig.getModel());
        data.put("deepSeekAvailable", deepSeekClientService.isAvailable());
        data.put("capabilities", List.of(
            "学生端报修智能识别",
            "维修工端 AI 维修报告",
            "管理员端反馈情感分析",
            "知识库 AI 草稿生成"
        ));

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "AI 状态加载完成");
        result.put("data", data);

        return ResponseEntity.ok(result);
    }

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

    @PostMapping("/analyze-feedback-sentiment")
    public ResponseEntity<Map<String, Object>> analyzeFeedbackSentiment(
            @Valid @RequestBody AnalyzeFeedbackSentimentRequest request) {

        AnalyzeFeedbackSentimentResponse response = aiAnalyzeService.analyzeFeedbackSentiment(request);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "AI feedback sentiment analysis completed");
        result.put("data", response);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/repair-report/generate")
    public ResponseEntity<Map<String, Object>> generateRepairReport(@RequestBody Map<String, String> request) {
        String report = aiAnalyzeService.generateRepairReport(
            request.get("description"),
            request.get("processNotes")
        );

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "维修报告生成完成");
        result.put("data", Map.of("report", report));

        return ResponseEntity.ok(result);
    }
}
