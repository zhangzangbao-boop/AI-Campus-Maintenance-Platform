package com.ligong.reportingcenter.service;

import com.ligong.reportingcenter.domain.entity.Rating;
import com.ligong.reportingcenter.domain.entity.RepairTicket;
import com.ligong.reportingcenter.domain.enums.TicketStatus;
import com.ligong.reportingcenter.repository.RatingRepository;
import com.ligong.reportingcenter.repository.TicketRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FacilityHealthService {

    private final TicketRepository ticketRepository;
    private final RatingRepository ratingRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getFacilityHealthIndex() {
        Map<String, AreaAccumulator> areas = new LinkedHashMap<>();
        Map<String, CategoryAccumulator> categories = new LinkedHashMap<>();

        for (RepairTicket ticket : ticketRepository.findAll()) {
            if (Boolean.TRUE.equals(ticket.getDeleted())) {
                continue;
            }
            String area = extractArea(ticket.getLocationText());
            String category = ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : "未分类";
            AreaAccumulator areaAccumulator = areas.computeIfAbsent(area, AreaAccumulator::new);
            CategoryAccumulator categoryAccumulator = categories.computeIfAbsent(category, CategoryAccumulator::new);

            areaAccumulator.accept(ticket, ratingRepository.findByTicket(ticket).map(Rating::getScore).orElse(null));
            categoryAccumulator.accept(ticket);
        }

        List<Map<String, Object>> areaHealth = areas.values().stream()
            .map(AreaAccumulator::toMap)
            .sorted(Comparator.comparingDouble(item -> -((Number) item.get("riskScore")).doubleValue()))
            .toList();

        List<Map<String, Object>> categoryRisk = categories.values().stream()
            .map(CategoryAccumulator::toMap)
            .sorted(Comparator.comparingDouble(item -> -((Number) item.get("riskScore")).doubleValue()))
            .toList();

        double avgScore = areaHealth.stream()
            .mapToDouble(item -> ((Number) item.get("healthScore")).doubleValue())
            .average()
            .orElse(100.0);

        Map<String, Object> result = new HashMap<>();
        result.put("overallHealthScore", Math.round(avgScore * 10.0) / 10.0);
        result.put("overallRiskLevel", riskLevel(100.0 - avgScore));
        result.put("areaHealth", areaHealth);
        result.put("categoryRisk", categoryRisk);
        result.put("suggestions", buildSuggestions(areaHealth, categoryRisk));
        result.put("generatedAt", LocalDateTime.now());
        return result;
    }

    private List<String> buildSuggestions(List<Map<String, Object>> areaHealth, List<Map<String, Object>> categoryRisk) {
        List<String> suggestions = new ArrayList<>();
        if (!areaHealth.isEmpty()) {
            Map<String, Object> top = areaHealth.get(0);
            suggestions.add("优先巡检“" + top.get("area") + "”，该区域近期报修和未闭环工单较集中。");
        }
        if (!categoryRisk.isEmpty()) {
            Map<String, Object> top = categoryRisk.get(0);
            suggestions.add("重点关注“" + top.get("category") + "”类问题，建议结合知识库沉淀标准处理方案。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("当前设施运行平稳，可保持常规巡检频率。");
        }
        return suggestions;
    }

    private String extractArea(String location) {
        if (location == null || location.isBlank()) {
            return "未知区域";
        }
        String normalized = location.trim();
        String[] tokens = normalized.split("[\\s-]+");
        return tokens.length == 0 || tokens[0].isBlank() ? normalized : tokens[0];
    }

    private static boolean active(TicketStatus status) {
        return status == TicketStatus.WAITING_ACCEPT || status == TicketStatus.IN_PROGRESS;
    }

    private static boolean completed(TicketStatus status) {
        return status == TicketStatus.RESOLVED
            || status == TicketStatus.WAITING_FEEDBACK
            || status == TicketStatus.FEEDBACKED
            || status == TicketStatus.CLOSED;
    }

    private static String riskLevel(double riskScore) {
        if (riskScore >= 60) {
            return "高风险";
        }
        if (riskScore >= 35) {
            return "中风险";
        }
        if (riskScore >= 15) {
            return "低风险";
        }
        return "健康";
    }

    private static class AreaAccumulator {
        private final String area;
        private int totalTickets;
        private int activeTickets;
        private int repeatedTickets;
        private int ratedTickets;
        private int ratingTotal;
        private long processingHoursTotal;
        private int processingSamples;
        private final Map<String, Integer> categoryCounts = new HashMap<>();

        AreaAccumulator(String area) {
            this.area = area;
        }

        void accept(RepairTicket ticket, Integer ratingScore) {
            totalTickets++;
            if (active(ticket.getStatus())) {
                activeTickets++;
            }
            String category = ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : "未分类";
            int categoryCount = categoryCounts.merge(category, 1, Integer::sum);
            if (categoryCount > 1) {
                repeatedTickets++;
            }
            if (ratingScore != null) {
                ratedTickets++;
                ratingTotal += ratingScore;
            }
            if (completed(ticket.getStatus()) && ticket.getCreatedAt() != null && ticket.getCompletedAt() != null) {
                processingHoursTotal += Math.max(0, Duration.between(ticket.getCreatedAt(), ticket.getCompletedAt()).toHours());
                processingSamples++;
            }
        }

        Map<String, Object> toMap() {
            double avgRating = ratedTickets == 0 ? 0.0 : ratingTotal * 1.0 / ratedTickets;
            double avgHours = processingSamples == 0 ? 0.0 : processingHoursTotal * 1.0 / processingSamples;
            double risk = Math.min(100.0,
                totalTickets * 3.0
                    + activeTickets * 8.0
                    + repeatedTickets * 6.0
                    + Math.min(avgHours, 120.0) * 0.15
                    - Math.max(0.0, avgRating - 3.0) * 5.0);
            double health = Math.max(0.0, 100.0 - risk);
            Map<String, Object> item = new HashMap<>();
            item.put("area", area);
            item.put("totalTickets", totalTickets);
            item.put("activeTickets", activeTickets);
            item.put("repeatedTickets", repeatedTickets);
            item.put("avgRating", Math.round(avgRating * 10.0) / 10.0);
            item.put("avgProcessingHours", Math.round(avgHours * 10.0) / 10.0);
            item.put("riskScore", Math.round(risk * 10.0) / 10.0);
            item.put("healthScore", Math.round(health * 10.0) / 10.0);
            item.put("riskLevel", riskLevel(risk));
            return item;
        }
    }

    private static class CategoryAccumulator {
        private final String category;
        private int totalTickets;
        private int activeTickets;
        private int highPriorityTickets;

        CategoryAccumulator(String category) {
            this.category = category;
        }

        void accept(RepairTicket ticket) {
            totalTickets++;
            if (active(ticket.getStatus())) {
                activeTickets++;
            }
            if ("high".equalsIgnoreCase(ticket.getPriority())) {
                highPriorityTickets++;
            }
        }

        Map<String, Object> toMap() {
            double risk = Math.min(100.0, totalTickets * 4.0 + activeTickets * 9.0 + highPriorityTickets * 10.0);
            Map<String, Object> item = new HashMap<>();
            item.put("category", category);
            item.put("totalTickets", totalTickets);
            item.put("activeTickets", activeTickets);
            item.put("highPriorityTickets", highPriorityTickets);
            item.put("riskScore", Math.round(risk * 10.0) / 10.0);
            item.put("riskLevel", riskLevel(risk));
            return item;
        }
    }
}
