package com.ligong.reportingcenter.controller;

import com.ligong.reportingcenter.domain.enums.TicketStatus;
import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.AnalysisDashboardDto;
import com.ligong.reportingcenter.dto.EfficiencyStatsDto;
import com.ligong.reportingcenter.dto.TicketSummaryDto;
import com.ligong.reportingcenter.dto.request.AiEnhancementRequest;
import com.ligong.reportingcenter.dto.response.AiEnhancementResponse;
import com.ligong.reportingcenter.service.AdminAnalysisService;
import com.ligong.reportingcenter.service.TicketService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员智慧分析控制器
 * 提供数据分析仪表盘、维修效率分析、AI增强分析预留接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminAnalysisController {

    private final AdminAnalysisService adminAnalysisService;
    private final TicketService ticketService;

    /**
     * 智慧分析仪表盘 - 综合数据概览
     */
    @GetMapping("/analysis/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getDashboard() {
        AnalysisDashboardDto dashboard = adminAnalysisService.getDashboard();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", dashboard);
        return result;
    }

    /**
     * 维修效率分析排行
     */
    @GetMapping("/analysis/efficiency")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getEfficiencyRanking() {
        List<EfficiencyStatsDto> ranking = adminAnalysisService.getEfficiencyRanking();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", ranking);
        return result;
    }

    /**
     * AI增强分析接口（预留）
     * 当前返回规则引擎统计结果，后续接入DeepSeek等大模型后激活AI分析能力
     */
    @PostMapping("/analysis/ai-enhance")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> aiEnhance(@RequestBody AiEnhancementRequest request) {
        AiEnhancementResponse response = adminAnalysisService.aiEnhance(request);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "分析完成");
        result.put("data", response);
        return result;
    }

    /**
     * AI增强分析状态查询
     * 检查AI模型是否已配置、是否可用
     */
    @GetMapping("/analysis/ai-status")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getAiStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("aiEnabled", false);  // 后续接入后改为动态检查
        status.put("modelProvider", "DeepSeek/OpenAI兼容（预留）");
        status.put("capabilities", List.of(
            "故障根因分析",
            "维修效率优化建议",
            "工单趋势预测",
            "设施健康评估",
            "知识库智能匹配"
        ));
        status.put("status", "PENDING");
        status.put("message", "AI增强分析模块已预留接口，配置API Key后即可激活使用。当前使用规则引擎提供基础统计。");

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", status);
        return result;
    }

    /**
     * 工单处理进度追踪（管理员视角）
     * 查看所有进行中工单的处理进展
     */
    @GetMapping("/analysis/progress-tracking")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getProgressTracking() {
        // 获取所有进行中的工单
        List<TicketSummaryDto> inProgressTickets = ticketService.listByStatus(TicketStatus.IN_PROGRESS);
        List<TicketSummaryDto> waitingAcceptTickets = ticketService.listByStatus(TicketStatus.WAITING_ACCEPT);

        Map<String, Object> tracking = new HashMap<>();
        tracking.put("waitingAccept", waitingAcceptTickets);   // 待接单
        tracking.put("inProgress", inProgressTickets);         // 维修中

        // 计算各状态数量
        Map<String, Long> statusCount = new HashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            statusCount.put(status.name(), ticketService.countByStatus(status));
        }
        tracking.put("statusCount", statusCount);

        // 进度摘要
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalActive", waitingAcceptTickets.size() + inProgressTickets.size());
        summary.put("waitingAcceptCount", waitingAcceptTickets.size());
        summary.put("inProgressCount", inProgressTickets.size());
        tracking.put("summary", summary);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", tracking);
        return result;
    }
}
