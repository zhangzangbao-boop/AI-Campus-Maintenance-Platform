package com.ligong.reportingcenter.controller;

import com.ligong.reportingcenter.dto.AiTicketAnalysisDto;
import com.ligong.reportingcenter.dto.SimilarTicketDto;
import com.ligong.reportingcenter.dto.request.AiKnowledgeDraftRequest;
import com.ligong.reportingcenter.dto.request.AiTicketAnalyzeRequest;
import com.ligong.reportingcenter.dto.request.SimilarTicketRequest;
import com.ligong.reportingcenter.service.AiAssistantService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiController {

    private final AiAssistantService aiAssistantService;

    @PostMapping("/ticket/analyze")
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
    public Map<String, Object> analyzeTicket(@RequestBody AiTicketAnalyzeRequest request) {
        return success(aiAssistantService.analyzeTicket(request), "智能报修分析完成");
    }

    @PostMapping("/ticket/similar")
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
    public Map<String, Object> similarTickets(@RequestBody SimilarTicketRequest request) {
        List<SimilarTicketDto> tickets = aiAssistantService.findSimilarTickets(request);
        return success(tickets, "相似工单检索完成");
    }

    @PostMapping("/repair-report/generate")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> generateRepairReport(@RequestBody Map<String, String> request) {
        String report = aiAssistantService.generateRepairReport(
            request.get("description"),
            request.get("processNotes")
        );
        return success(Map.of("report", report), "维修报告生成完成");
    }

    @GetMapping("/ticket/{ticketId}/summary")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public Map<String, Object> summarizeTicket(@PathVariable("ticketId") Long ticketId) {
        return success(aiAssistantService.summarizeTicket(ticketId), "工单摘要生成完成");
    }

    @PostMapping("/knowledge/draft")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> draftKnowledge(@RequestBody AiKnowledgeDraftRequest request) {
        return success(aiAssistantService.draftKnowledge(request), "知识库草稿生成完成");
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
    public Map<String, Object> status() {
        return success(aiAssistantService.getAiStatus(), "获取成功");
    }

    private Map<String, Object> success(Object data, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }
}
