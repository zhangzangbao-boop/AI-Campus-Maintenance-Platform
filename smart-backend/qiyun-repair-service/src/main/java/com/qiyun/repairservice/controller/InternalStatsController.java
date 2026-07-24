package com.qiyun.repairservice.controller;

import com.qiyun.repairservice.domain.entity.Rating;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.dto.LocationStatsDto;
import com.qiyun.repairservice.dto.PagedResult;
import com.qiyun.repairservice.dto.RatingDto;
import com.qiyun.repairservice.dto.RepairmanRatingStatsDto;
import com.qiyun.repairservice.dto.request.FeedbackFollowUpUpdateRequest;
import com.qiyun.repairservice.repository.RatingRepository;
import com.qiyun.repairservice.repository.TicketRepository;
import com.qiyun.repairservice.service.FacilityHealthService;
import com.qiyun.repairservice.service.FeedbackFollowUpService;
import com.qiyun.repairservice.service.FeedbackSentimentAnalysisService;
import com.qiyun.repairservice.service.RatingDtoMapper;
import com.qiyun.repairservice.service.TicketService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部统计接口控制器
 * 仅供ops-service通过Feign调用，不经过Gateway
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/repair")
public class InternalStatsController {

    private final TicketRepository ticketRepository;
    private final TicketService ticketService;
    private final RatingRepository ratingRepository;
    private final FacilityHealthService facilityHealthService;
    private final RatingDtoMapper ratingDtoMapper;
    private final FeedbackFollowUpService feedbackFollowUpService;
    private final FeedbackSentimentAnalysisService feedbackSentimentAnalysisService;

    /**
     * 获取工单状态统计
     */
    @GetMapping("/stats/status")
    public ResponseEntity<Map<String, Object>> getStatusStats() {
        List<Map<String, Object>> statusStats = new ArrayList<>();
        for (TicketStatus status : TicketStatus.values()) {
            Map<String, Object> item = new HashMap<>();
            item.put("status", status.name());
            item.put("count", ticketRepository.countByStatus(status));
            item.put("value", ticketRepository.countByStatus(status));
            statusStats.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", statusStats);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取分类统计
     */
    @GetMapping("/stats/category")
    public ResponseEntity<Map<String, Object>> getCategoryStats() {
        List<Map<String, Object>> categoryStats = ticketService.getCategoryStatsFromView();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", categoryStats);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取地点统计
     */
    @GetMapping("/stats/location")
    public ResponseEntity<Map<String, Object>> getLocationStats() {
        List<LocationStatsDto> locationStats = ticketService.getLocationStats();
        List<Map<String, Object>> locationData = locationStats.stream()
            .map(dto -> {
                Map<String, Object> item = new HashMap<>();
                item.put("location", dto.location());
                item.put("name", dto.location());
                item.put("count", dto.count());
                item.put("value", dto.count());
                return item;
            })
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", locationData);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取月度统计
     */
    @GetMapping("/stats/monthly")
    public ResponseEntity<Map<String, Object>> getMonthlyStats() {
        Map<String, Object> monthlyStats = ticketService.getMonthlyStats();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", monthlyStats);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取维修工评分统计
     */
    @GetMapping("/stats/repairman-rating")
    public ResponseEntity<Map<String, Object>> getRepairmanRatingStats() {
        List<RepairmanRatingStatsDto> ratingStats = ticketService.getRepairmanRatingStats();
        List<Map<String, Object>> ratingData = ratingStats.stream()
            .map(dto -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", dto.id());
                item.put("name", dto.name());
                item.put("rating", dto.rating());
                item.put("completedOrders", dto.completedOrders());
                item.put("count", dto.completedOrders());
                return item;
            })
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", ratingData);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取平均处理时间
     */
    @GetMapping("/stats/processing-time")
    public ResponseEntity<Map<String, Object>> getProcessingTimeStats() {
        Map<String, Object> processingTimeStats = ticketService.getAverageProcessingTime();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", processingTimeStats);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取SLA概览
     */
    @GetMapping("/stats/sla")
    public ResponseEntity<Map<String, Object>> getSlaOverview() {
        Map<String, Object> slaOverview = ticketService.getSlaOverview();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", slaOverview);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取热点分析
     */
    @GetMapping("/stats/hotspot")
    public ResponseEntity<Map<String, Object>> getHotspotAnalysis() {
        Map<String, Object> hotspotAnalysis = ticketService.getHotspotAnalysis();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", hotspotAnalysis);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取设施健康数据
     */
    @GetMapping("/stats/facility-health")
    public ResponseEntity<Map<String, Object>> getFacilityHealth() {
        Map<String, Object> facilityHealth = facilityHealthService.getFacilityHealthIndex();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", facilityHealth);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取按地点和分类聚合的高频故障趋势
     */
    @GetMapping("/stats/fault-trends")
    public ResponseEntity<Map<String, Object>> getFaultTrendStats(
            @RequestParam(value = "days", defaultValue = "7") int days) {
        int safeDays = days == 30 ? 30 : 7;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime recentStart = now.minusDays(safeDays);
        LocalDateTime previousStart = now.minusDays(safeDays * 2L);

        List<Map<String, Object>> trendData = ticketRepository.findFaultTrendStats(recentStart, previousStart).stream()
            .map(row -> {
                Map<String, Object> item = new HashMap<>();
                long recentCount = toLong(row[2]);
                long previousCount = toLong(row[3]);
                item.put("location", row[0] == null ? "未知位置" : String.valueOf(row[0]));
                item.put("category", row[1] == null ? "未分类" : String.valueOf(row[1]));
                item.put("periodDays", safeDays);
                item.put("ticketCount", recentCount);
                item.put("previousCount", previousCount);
                item.put("growthRate", calculateGrowthRate(recentCount, previousCount));
                item.put("lastCreatedAt", row[4]);
                return item;
            })
            .collect(Collectors.toList());

        Map<String, Object> payload = new HashMap<>();
        payload.put("periodDays", safeDays);
        payload.put("generatedAt", now);
        payload.put("items", trendData);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", payload);
        return ResponseEntity.ok(result);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private double calculateGrowthRate(long recentCount, long previousCount) {
        if (previousCount <= 0) {
            return recentCount > 0 ? 100.0 : 0.0;
        }
        return Math.round(((recentCount - previousCount) * 10000.0 / previousCount)) / 100.0;
    }

    @GetMapping("/tickets/export")
    public ResponseEntity<Map<String, Object>> getTicketsForExport(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "limit", defaultValue = "5000") int limit) {
        List<com.qiyun.repairservice.dto.TicketSummaryDto> tickets = filterTickets(
                status, category, keyword, includeDeleted, startDate, endDate
            )
            .stream()
            .limit(Math.max(1, Math.min(limit, 5000)))
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", Map.of(
            "list", tickets,
            "total", tickets.size()
        ));
        return ResponseEntity.ok(result);
    }

    private List<com.qiyun.repairservice.dto.TicketSummaryDto> filterTickets(
            String status,
            String category,
            String keyword,
            boolean includeDeleted,
            String startDate,
            String endDate) {
        java.util.stream.Stream<com.qiyun.repairservice.dto.TicketSummaryDto> stream =
            ticketService.listAll(includeDeleted).stream();
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            TicketStatus targetStatus = mapStatusFromFrontend(status);
            stream = stream.filter(t -> t.status() == targetStatus);
        }
        if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
            stream = stream.filter(t -> category.equalsIgnoreCase(t.categoryName()));
        }
        if (keyword != null && !keyword.isBlank()) {
            String searchKeyword = keyword.toLowerCase().trim();
            stream = stream.filter(t -> {
                String desc = t.description() != null ? t.description().toLowerCase() : "";
                String loc = t.locationText() != null ? t.locationText().toLowerCase() : "";
                String ticketId = t.ticketId() != null ? String.valueOf(t.ticketId()) : "";
                String studentId = t.studentId() != null ? t.studentId().toLowerCase() : "";
                String staffId = t.staffId() != null ? t.staffId().toLowerCase() : "";
                String categoryName = t.categoryName() != null ? t.categoryName().toLowerCase() : "";
                return desc.contains(searchKeyword)
                    || loc.contains(searchKeyword)
                    || ticketId.contains(searchKeyword)
                    || studentId.contains(searchKeyword)
                    || staffId.contains(searchKeyword)
                    || categoryName.contains(searchKeyword);
            });
        }
        LocalDateTime startAt = parseStartDate(startDate);
        LocalDateTime endAt = parseEndDate(endDate);
        if (startAt != null) {
            stream = stream.filter(t -> t.createdAt() != null && !t.createdAt().isBefore(startAt));
        }
        if (endAt != null) {
            stream = stream.filter(t -> t.createdAt() != null && !t.createdAt().isAfter(endAt));
        }
        return stream.toList();
    }

    private LocalDateTime parseStartDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim()).atStartOfDay();
    }

    private LocalDateTime parseEndDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim()).atTime(LocalTime.MAX);
    }

    private TicketStatus mapStatusFromFrontend(String frontendStatus) {
        String status = frontendStatus.toLowerCase();
        return switch (status) {
            case "pending" -> TicketStatus.WAITING_ACCEPT;
            case "processing" -> TicketStatus.IN_PROGRESS;
            case "completed", "resolved" -> TicketStatus.RESOLVED;
            case "to_be_evaluated", "waiting_feedback" -> TicketStatus.WAITING_FEEDBACK;
            case "closed" -> TicketStatus.CLOSED;
            case "rejected" -> TicketStatus.REJECTED;
            default -> TicketStatus.valueOf(frontendStatus.toUpperCase());
        };
    }

    /**
     * 获取评价列表（分页）
     */
    @GetMapping("/feedbacks")
    public ResponseEntity<Map<String, Object>> getFeedbacks(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "lowRating", required = false) Boolean lowRating,
            @RequestParam(value = "sentiment", required = false) String sentiment,
            @RequestParam(value = "followUpStatus", required = false) String followUpStatus,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {

        List<Rating> ratings = ratingRepository.findAllWithDetails();
        if (backfillMissingSentiment(ratings)) {
            ratings = ratingRepository.findAllWithDetails();
        }

        List<RatingDto> allRatings = ratings.stream()
            .map(ratingDtoMapper::toDto)
            .sorted(Comparator.comparing(
                RatingDto::ratedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
            ))
            .collect(Collectors.toList());

        LocalDateTime startAt = parseStartDate(startDate);
        LocalDateTime endAt = parseEndDate(endDate);
        if (startAt != null) {
            allRatings = allRatings.stream()
                .filter(r -> r.ratedAt() != null && !r.ratedAt().isBefore(startAt))
                .collect(Collectors.toList());
        }
        if (endAt != null) {
            allRatings = allRatings.stream()
                .filter(r -> r.ratedAt() != null && !r.ratedAt().isAfter(endAt))
                .collect(Collectors.toList());
        }

        // 低评分筛选
        if (lowRating != null && lowRating) {
            allRatings = allRatings.stream()
                .filter(r -> r.score() <= 2)
                .collect(Collectors.toList());
        }

        if (followUpStatus != null && !followUpStatus.isBlank()) {
            String normalizedFollowUpStatus = normalizeFollowUpStatusFilter(followUpStatus);
            allRatings = allRatings.stream()
                .filter(r -> normalizedFollowUpStatus.equals(r.followUpStatus()))
                .collect(Collectors.toList());
        }

        Map<String, Long> sentimentCounts = new HashMap<>();
        sentimentCounts.put("POSITIVE", allRatings.stream().filter(r -> "POSITIVE".equals(r.sentiment()) && !isNegativeFeedback(r)).count());
        sentimentCounts.put("NEUTRAL", allRatings.stream().filter(r -> "NEUTRAL".equals(r.sentiment()) && !isNegativeFeedback(r)).count());
        sentimentCounts.put("NEGATIVE", allRatings.stream().filter(this::isNegativeFeedback).count());

        if (sentiment != null && !sentiment.isBlank()) {
            String normalizedSentiment = sentiment.trim().toUpperCase();
            allRatings = allRatings.stream()
                .filter(r -> matchesSentimentFilter(r, normalizedSentiment))
                .collect(Collectors.toList());
        }

        int total = allRatings.size();
        long followUpTotal = allRatings.stream()
            .filter(r -> "PENDING".equals(r.followUpStatus()))
            .count();
        double averageRating = allRatings.stream()
            .filter(r -> r.score() != null)
            .mapToInt(RatingDto::score)
            .average()
            .orElse(0);
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<RatingDto> pagedRatings = fromIndex < total ?
            allRatings.subList(fromIndex, toIndex) : List.of();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", Map.of(
            "list", pagedRatings,
            "total", total,
            "averageRating", averageRating,
            "followUpTotal", followUpTotal,
            "sentimentCounts", sentimentCounts
        ));
        return ResponseEntity.ok(result);
    }

    private boolean matchesSentimentFilter(RatingDto rating, String sentiment) {
        if ("NEGATIVE".equals(sentiment)) {
            return isNegativeFeedback(rating);
        }
        if ("POSITIVE".equals(sentiment) || "NEUTRAL".equals(sentiment)) {
            return sentiment.equals(rating.sentiment()) && !isNegativeFeedback(rating);
        }
        return sentiment.equals(rating.sentiment());
    }

    private boolean isNegativeFeedback(RatingDto rating) {
        return (rating.score() != null && rating.score() <= 2)
            || "NEGATIVE".equals(rating.sentiment())
            || !Boolean.TRUE.equals(rating.resolved());
    }

    private String normalizeFollowUpStatusFilter(String followUpStatus) {
        String normalized = followUpStatus.trim().toUpperCase();
        return switch (normalized) {
            case "RESOLVED" -> "HANDLED";
            case "PROCESSING" -> "PENDING";
            default -> normalized;
        };
    }

    private boolean backfillMissingSentiment(List<Rating> ratings) {
        boolean updated = false;
        for (Rating rating : ratings) {
            if (rating.getRatingId() == null || rating.getSentiment() != null) {
                continue;
            }
            try {
                feedbackSentimentAnalysisService.analyzeNow(rating.getRatingId());
                updated = true;
            } catch (Exception e) {
                log.warn("补充分评价情感分析失败: ratingId={}, error={}", rating.getRatingId(), e.getMessage());
            }
        }
        return updated;
    }

    @PutMapping("/feedbacks/{ratingId}/follow-up")
    public ResponseEntity<Map<String, Object>> updateFeedbackFollowUp(
            @PathVariable Long ratingId,
            @RequestBody FeedbackFollowUpUpdateRequest request) {
        RatingDto rating = feedbackFollowUpService.updateFollowUp(ratingId, request);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "Updated");
        result.put("data", rating);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取评价数量
     */
    @GetMapping("/feedbacks/count")
    public ResponseEntity<Long> getFeedbacksCount() {
        return ResponseEntity.ok(ratingRepository.count());
    }
}
