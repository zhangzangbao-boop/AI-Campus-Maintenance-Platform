package com.qiyun.repairservice.controller;

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
import com.qiyun.repairservice.service.RatingDtoMapper;
import com.qiyun.repairservice.service.TicketService;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
     * 获取评价列表（分页）
     */
    @GetMapping("/feedbacks")
    public ResponseEntity<Map<String, Object>> getFeedbacks(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "lowRating", required = false) Boolean lowRating,
            @RequestParam(value = "sentiment", required = false) String sentiment,
            @RequestParam(value = "followUpStatus", required = false) String followUpStatus) {

        List<RatingDto> allRatings = ratingRepository.findAllWithDetails().stream()
            .map(ratingDtoMapper::toDto)
            .collect(Collectors.toList());

        // 低评分筛选
        if (lowRating != null && lowRating) {
            allRatings = allRatings.stream()
                .filter(r -> r.score() <= 2)
                .collect(Collectors.toList());
        }

        // 简单分页
        if (sentiment != null && !sentiment.isBlank()) {
            String normalizedSentiment = sentiment.trim().toUpperCase();
            allRatings = allRatings.stream()
                .filter(r -> normalizedSentiment.equals(r.sentiment()))
                .collect(Collectors.toList());
        }

        if (followUpStatus != null && !followUpStatus.isBlank()) {
            String normalizedFollowUpStatus = followUpStatus.trim().toUpperCase();
            allRatings = allRatings.stream()
                .filter(r -> normalizedFollowUpStatus.equals(r.followUpStatus()))
                .collect(Collectors.toList());
        }

        int total = allRatings.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<RatingDto> pagedRatings = fromIndex < total ?
            allRatings.subList(fromIndex, toIndex) : List.of();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", Map.of(
            "list", pagedRatings,
            "total", total
        ));
        return ResponseEntity.ok(result);
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
