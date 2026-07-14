package com.qiyun.repairservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.feign.client.AiServiceClient;
import com.qiyun.feign.dto.AnalyzeTicketRequest;
import com.qiyun.feign.dto.AnalyzeTicketResponse;
import com.qiyun.repairservice.domain.entity.AiTicketAnalysis;
import com.qiyun.repairservice.repository.AiTicketAnalysisRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 分析集成服务
 * 通过 Feign 调用 qiyun-ai-service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisIntegrationService {

    private final AiServiceClient aiServiceClient;
    private final AiTicketAnalysisRepository aiTicketAnalysisRepository;
    private final ObjectMapper objectMapper;

    /**
     * 分析工单并保存结果
     *
     * @param ticketId   工单ID
     * @param description 问题描述
     * @param location    位置信息
     * @return AI 分析结果
     */
    @Transactional
    public AiTicketAnalysis analyzeAndSave(Long ticketId, String description, String location) {
        log.info("开始 AI 分析: ticketId={}, description={}", ticketId, description);

        try {
            // 1. 调用 AI 服务
            AnalyzeTicketRequest request = new AnalyzeTicketRequest(description, location);
            Map<String, Object> responseMap = aiServiceClient.analyzeTicket(request);
            AnalyzeTicketResponse response = aiServiceClient.extractResponse(responseMap);

            if (response == null) {
                log.warn("AI 分析返回空结果: ticketId={}", ticketId);
                return null;
            }

            log.info("AI 分析完成: category={}, urgency={}", response.category(), response.urgency());

            // 2. 保存分析结果
            AiTicketAnalysis analysis = new AiTicketAnalysis();
            analysis.setTicketId(ticketId);
            analysis.setSourceText(description);
            analysis.setCategoryKey(response.category());
            analysis.setLocationText(location);
            analysis.setUrgency(response.urgency());
            analysis.setSuggestion(response.suggestion());
            analysis.setKeywords(toJsonString(response.keywords()));
            analysis.setProvider("qiyun-ai-service");
            analysis.setModel("规则匹配引擎-v1");
            analysis.setCreatedAt(LocalDateTime.now());

            // 设置紧急程度对应的优先级
            analysis.setPriority(mapUrgencyToPriority(response.urgency()));

            aiTicketAnalysisRepository.save(analysis);
            log.info("AI 分析结果已保存: analysisId={}", analysis.getAnalysisId());

            return analysis;

        } catch (Exception e) {
            log.error("AI 分析失败: ticketId={}, error={}", ticketId, e.getMessage(), e);
            // 返回 null 表示分析失败，但不影响工单创建
            return null;
        }
    }

    /**
     * 仅分析不保存（用于前端实时展示）
     */
    public AnalyzeTicketResponse analyzeOnly(String description, String location) {
        try {
            AnalyzeTicketRequest request = new AnalyzeTicketRequest(description, location);
            Map<String, Object> responseMap = aiServiceClient.analyzeTicket(request);
            return aiServiceClient.extractResponse(responseMap);
        } catch (Exception e) {
            log.error("AI 分析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据工单ID获取分析结果
     */
    @Transactional(readOnly = true)
    public AiTicketAnalysis getByTicketId(Long ticketId) {
        return aiTicketAnalysisRepository.findByTicketId(ticketId).orElse(null);
    }

    /**
     * 将紧急程度映射为优先级
     */
    private String mapUrgencyToPriority(String urgency) {
        if (urgency == null) {
            return "medium";
        }
        return switch (urgency) {
            case "紧急" -> "high";
            case "普通" -> "medium";
            case "一般" -> "low";
            default -> "medium";
        };
    }

    /**
     * 将列表转换为 JSON 字符串
     */
    private String toJsonString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("关键词序列化失败: {}", e.getMessage());
            return "[]";
        }
    }
}