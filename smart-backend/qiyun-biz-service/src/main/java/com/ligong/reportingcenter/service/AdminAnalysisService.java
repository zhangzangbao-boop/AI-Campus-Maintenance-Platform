package com.ligong.reportingcenter.service;

import com.ligong.reportingcenter.domain.entity.RepairTicket;
import com.ligong.reportingcenter.domain.entity.User;
import com.ligong.reportingcenter.domain.enums.TicketStatus;
import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.AnalysisDashboardDto;
import com.ligong.reportingcenter.dto.EfficiencyStatsDto;
import com.ligong.reportingcenter.dto.response.AiEnhancementResponse;
import com.ligong.reportingcenter.dto.request.AiEnhancementRequest;
import com.ligong.reportingcenter.repository.TicketRepository;
import com.ligong.reportingcenter.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员智慧分析服务
 * 提供数据分析仪表盘、维修效率分析、AI增强分析预留接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnalysisService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    /**
     * 获取管理员智慧分析仪表盘数据
     */
    @Transactional(readOnly = true)
    public AnalysisDashboardDto getDashboard() {
        List<RepairTicket> allTickets = ticketRepository.findAll()
            .stream()
            .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
            .toList();

        // 概览统计
        long totalTickets = allTickets.size();
        long pendingTickets = countByStatus(allTickets, TicketStatus.WAITING_ACCEPT);
        long inProgressTickets = countByStatus(allTickets, TicketStatus.IN_PROGRESS);
        long resolvedTickets = countByStatus(allTickets, TicketStatus.RESOLVED)
            + countByStatus(allTickets, TicketStatus.WAITING_FEEDBACK)
            + countByStatus(allTickets, TicketStatus.FEEDBACKED);
        long closedTickets = countByStatus(allTickets, TicketStatus.CLOSED);

        // 效率指标
        Double avgHours = ticketRepository.findAverageProcessingTimeHours();
        double avgProcessingHours = avgHours != null ? avgHours : 0;
        String avgProcessingDisplay = formatHoursDisplay(avgProcessingHours);
        double slaComplianceRate = calculateSlaComplianceRate(allTickets);

        // 故障类型分布
        List<Map<String, Object>> categoryDistribution = buildCategoryDistribution(allTickets);

        // 高频故障 Top 5
        List<Map<String, Object>> highFrequencyFaults = buildHighFrequencyFaults(allTickets);

        // 月度趋势
        List<Map<String, Object>> monthlyTrend = buildMonthlyTrend(allTickets);

        // 维修效率排行
        List<Map<String, Object>> efficiencyRanking = buildEfficiencyRanking(allTickets);

        // AI增强分析预留
        Map<String, Object> aiInsights = new HashMap<>();
        aiInsights.put("status", "PENDING");
        aiInsights.put("message", "AI增强分析模块已预留，后续可接入DeepSeek等大模型进行智能分析。当前展示基于规则引擎的统计分析结果。");

        return new AnalysisDashboardDto(
            totalTickets, pendingTickets, inProgressTickets,
            resolvedTickets, closedTickets,
            avgProcessingHours, avgProcessingDisplay, slaComplianceRate,
            categoryDistribution, highFrequencyFaults, monthlyTrend,
            efficiencyRanking,
            "PENDING", aiInsights
        );
    }

    /**
     * 维修效率分析排行（维修人员维度）
     */
    @Transactional(readOnly = true)
    public List<EfficiencyStatsDto> getEfficiencyRanking() {
        List<User> staffUsers = userRepository.findByRoleAndIsActiveTrue(UserRole.STAFF);
        List<RepairTicket> allTickets = ticketRepository.findAll()
            .stream()
            .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
            .toList();

        List<EfficiencyStatsDto> ranking = new ArrayList<>();
        for (User staff : staffUsers) {
            String staffId = staff.getUserId();
            List<RepairTicket> staffTickets = allTickets.stream()
                .filter(t -> t.getStaff() != null && staffId.equals(t.getStaff().getUserId()))
                .toList();

            long totalAssigned = staffTickets.size();
            long completedTickets = staffTickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.RESOLVED
                    || t.getStatus() == TicketStatus.WAITING_FEEDBACK
                    || t.getStatus() == TicketStatus.FEEDBACKED
                    || t.getStatus() == TicketStatus.CLOSED)
                .count();
            long activeTickets = totalAssigned - completedTickets;
            double completionRate = totalAssigned > 0 ? (double) completedTickets / totalAssigned * 100 : 0;

            double avgProcHours = calculateAvgProcessingHours(staffTickets);
            String display = formatHoursDisplay(avgProcHours);

            Double avgRating = ticketRepository.findAverageRatingByStaffId(staffId);
            double rating = avgRating != null ? avgRating : 0;

            // 综合效率分：完成率30% + 处理速度30% + 评分40%
            double speedScore = avgProcHours > 0 ? Math.min(100, 72.0 / avgProcHours * 100) : 50;
            double ratingScore = rating > 0 ? rating / 5.0 * 100 : 50;
            double efficiencyScore = completionRate * 0.3 + speedScore * 0.3 + ratingScore * 0.4;

            ranking.add(new EfficiencyStatsDto(
                staffId,
                staff.getNickname() != null ? staff.getNickname() : staffId,
                totalAssigned, completedTickets, activeTickets,
                Math.round(completionRate * 10.0) / 10.0,
                Math.round(avgProcHours * 10.0) / 10.0,
                display,
                Math.round(rating * 10.0) / 10.0,
                Math.round(efficiencyScore * 10.0) / 10.0
            ));
        }

        ranking.sort(Comparator.comparingDouble(EfficiencyStatsDto::efficiencyScore).reversed());
        return ranking;
    }

    /**
     * AI增强分析预留接口
     * 当前返回占位信息，后续接入DeepSeek等大模型/知识库后可激活
     */
    public AiEnhancementResponse aiEnhance(AiEnhancementRequest request) {
        String analysisType = request.analysisType() != null ? request.analysisType() : "fault_analysis";

        log.info("AI增强分析请求 - 类型: {}, 使用知识库: {}", analysisType, request.useKnowledgeBase());

        Map<String, Object> result = new HashMap<>();
        result.put("analysisType", analysisType);
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("dataSource", "数据库统计（基于规则引擎）");

        // 根据分析类型返回不同的统计结果
        switch (analysisType) {
            case "fault_analysis":
                result.put("summary", "当前基于规则引擎的故障分析结果，后续接入大模型可提供更深度的故障根因分析和预测。");
                result.put("topCategories", buildCategoryDistribution(
                    ticketRepository.findAll().stream()
                        .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                        .toList()
                ));
                break;
            case "efficiency_analysis":
                result.put("summary", "维修效率分析结果，后续AI可自动识别瓶颈环节并给出优化建议。");
                result.put("rankings", getEfficiencyRanking());
                break;
            case "trend_prediction":
                result.put("summary", "趋势预测功能预留。后续接入大模型后可根据历史数据预测未来工单趋势。");
                result.put("currentTrend", buildMonthlyTrend(
                    ticketRepository.findAll().stream()
                        .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                        .toList()
                ));
                break;
            case "hotspot_analysis":
                result.put("summary", "热点分析功能预留。后续AI可智能识别校园设施薄弱环节。");
                result.put("hotAreas", ticketRepository.findHotAreaStats().stream()
                    .map(row -> Map.of("area", row[0], "totalTickets", row[1], "activeTickets", row[2]))
                    .toList());
                break;
            default:
                result.put("summary", "未知分析类型，已返回基础统计信息。");
        }

        return new AiEnhancementResponse(
            analysisType,
            "PENDING",
            "AI增强分析接口已就绪，当前返回规则引擎统计结果。配置DeepSeek API Key后可激活大模型增强分析。",
            result,
            request.useKnowledgeBase() ? "知识库增强功能预留，需后续配置" : null,
            "预留模型接口 - DeepSeek/OpenAI兼容"
        );
    }

    // ===== 私有辅助方法 =====

    private long countByStatus(List<RepairTicket> tickets, TicketStatus status) {
        return tickets.stream().filter(t -> t.getStatus() == status).count();
    }

    private double calculateSlaComplianceRate(List<RepairTicket> tickets) {
        long completedCount = tickets.stream()
            .filter(t -> t.getStatus() == TicketStatus.RESOLVED
                || t.getStatus() == TicketStatus.WAITING_FEEDBACK
                || t.getStatus() == TicketStatus.FEEDBACKED
                || t.getStatus() == TicketStatus.CLOSED)
            .count();
        if (completedCount == 0) return 0;

        long compliantCount = tickets.stream()
            .filter(t -> {
                if (t.getCompletedAt() == null || t.getCreatedAt() == null) return false;
                // 72小时为中等优先级SLA
                long hours = java.time.Duration.between(t.getCreatedAt(), t.getCompletedAt()).toHours();
                return hours <= 72;
            })
            .count();

        return Math.round((double) compliantCount / completedCount * 1000.0) / 10.0;
    }

    private List<Map<String, Object>> buildCategoryDistribution(List<RepairTicket> tickets) {
        Map<String, Long> categoryCount = tickets.stream()
            .filter(t -> t.getCategory() != null)
            .collect(Collectors.groupingBy(
                t -> t.getCategory().getCategoryName(),
                Collectors.counting()
            ));

        return categoryCount.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(e -> {
                Map<String, Object> item = new HashMap<>();
                item.put("type", e.getKey());
                item.put("name", e.getKey());
                item.put("value", e.getValue());
                item.put("count", e.getValue());
                return item;
            })
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildHighFrequencyFaults(List<RepairTicket> tickets) {
        // 按位置+分类组合统计高频故障
        Map<String, Long> faultCount = tickets.stream()
            .filter(t -> t.getLocationText() != null && t.getCategory() != null)
            .collect(Collectors.groupingBy(
                t -> t.getCategory().getCategoryName() + " - " + t.getLocationText(),
                Collectors.counting()
            ));

        return faultCount.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(e -> {
                Map<String, Object> item = new HashMap<>();
                item.put("fault", e.getKey());
                item.put("count", e.getValue());
                return item;
            })
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildMonthlyTrend(List<RepairTicket> tickets) {
        Map<String, Long> monthCount = tickets.stream()
            .filter(t -> t.getCreatedAt() != null)
            .collect(Collectors.groupingBy(
                t -> t.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                TreeMap::new,
                Collectors.counting()
            ));

        // 只保留最近12个月
        return monthCount.entrySet().stream()
            .skip(Math.max(0, monthCount.size() - 12))
            .map(e -> {
                Map<String, Object> item = new HashMap<>();
                item.put("month", e.getKey());
                item.put("orders", e.getValue());
                return item;
            })
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildEfficiencyRanking(List<RepairTicket> tickets) {
        // 按维修人员统计处理效率
        Map<String, List<RepairTicket>> staffTickets = tickets.stream()
            .filter(t -> t.getStaff() != null)
            .collect(Collectors.groupingBy(t -> t.getStaff().getUserId()));

        return staffTickets.entrySet().stream()
            .map(e -> {
                String staffId = e.getKey();
                List<RepairTicket> stList = e.getValue();
                long completed = stList.stream()
                    .filter(t -> t.getStatus() == TicketStatus.RESOLVED
                        || t.getStatus() == TicketStatus.WAITING_FEEDBACK
                        || t.getStatus() == TicketStatus.FEEDBACKED
                        || t.getStatus() == TicketStatus.CLOSED)
                    .count();
                double avgH = calculateAvgProcessingHours(stList);
                double avgRating = Optional.ofNullable(ticketRepository.findAverageRatingByStaffId(staffId)).orElse(0.0);

                Map<String, Object> item = new HashMap<>();
                item.put("staffId", staffId);
                item.put("completed", completed);
                item.put("avgHours", Math.round(avgH * 10.0) / 10.0);
                item.put("displayHours", formatHoursDisplay(avgH));
                item.put("avgRating", Math.round(avgRating * 10.0) / 10.0);
                return item;
            })
            .sorted((a, b) -> Double.compare(
                (double) b.get("completed"),
                (double) a.get("completed")
            ))
            .limit(10)
            .collect(Collectors.toList());
    }

    private double calculateAvgProcessingHours(List<RepairTicket> tickets) {
        List<RepairTicket> completed = tickets.stream()
            .filter(t -> t.getCompletedAt() != null && t.getCreatedAt() != null)
            .toList();
        if (completed.isEmpty()) return 0;
        return completed.stream()
            .mapToLong(t -> java.time.Duration.between(t.getCreatedAt(), t.getCompletedAt()).toHours())
            .average()
            .orElse(0);
    }

    private String formatHoursDisplay(double hours) {
        if (hours <= 0) return "暂无数据";
        if (hours >= 24) {
            double days = hours / 24.0;
            return String.format(Locale.ROOT, "约 %.1f 天", days);
        }
        return String.format(Locale.ROOT, "约 %.0f 小时", hours);
    }
}
