package com.ligong.reportingcenter.controller;

import com.ligong.reportingcenter.dto.PagedResult;
import com.ligong.reportingcenter.dto.RatingDto;
import com.ligong.reportingcenter.service.TicketService;
import com.ligong.reportingcenter.service.RatingService;
import com.ligong.reportingcenter.service.CategoryService;
import com.ligong.reportingcenter.service.AuditLogService;
import com.ligong.reportingcenter.domain.enums.TicketStatus;
import com.qiyun.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员控制器 - 工单、评价、统计等业务管理
 * 用户管理已迁移到qiyun-user-service
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final TicketService ticketService;
    private final RatingService ratingService;
    private final AuditLogService auditLogService;

    // 评价管理
    @GetMapping("/feedbacks")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResult<RatingDto> listFeedbacks(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            log.info("管理员请求获取评价列表: page={}, size={}", page, size);
            List<RatingDto> allRatings = ratingService.listAll();
            log.info("查询到 {} 条评价记录", allRatings.size());

            // 简单分页模拟
            int total = allRatings.size();
            int fromIndex = Math.min(page * size, total);
            int toIndex = Math.min(fromIndex + size, total);
            List<RatingDto> pagedRatings = fromIndex < total ?
                allRatings.subList(fromIndex, toIndex) : List.of();

            PagedResult<RatingDto> result = new PagedResult<>(pagedRatings, total);
            log.info("返回分页结果: list.size={}, total={}", result.getList().size(), result.getTotal());
            return result;
        } catch (Exception e) {
            log.error("获取评价列表失败", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "获取评价列表失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/feedbacks/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeedback(@PathVariable("id") Long id) {
        log.info("管理员请求删除评价: id={}", id);
        ratingService.delete(id);
        auditLogService.record("评价管理", "删除评价", "FEEDBACK", String.valueOf(id), "删除评价记录：" + id);
    }

    // 数据统计
    @GetMapping("/stats/category")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getCategoryStats() {
        // 基于数据库视图 vw_category_stats 的统计结果
        List<Map<String, Object>> categoryStats = ticketService.getCategoryStatsFromView();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", categoryStats);
        return result;
    }

    @GetMapping("/stats/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getStatusStats() {
        List<Map<String, Object>> statusStats = new java.util.ArrayList<>();
        for (TicketStatus status : TicketStatus.values()) {
            Map<String, Object> item = new HashMap<>();
            item.put("status", status.name());
            item.put("count", ticketService.countByStatus(status));
            item.put("value", ticketService.countByStatus(status));
            statusStats.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", statusStats);
        return result;
    }

    @GetMapping("/stats/location")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getLocationStats() {
        List<com.ligong.reportingcenter.dto.LocationStatsDto> locationStats = ticketService.getLocationStats();
        List<Map<String, Object>> locationData = locationStats.stream()
            .map(dto -> {
                Map<String, Object> item = new HashMap<>();
                item.put("location", dto.location());
                item.put("name", dto.location());
                item.put("count", dto.count());
                item.put("value", dto.count());
                return item;
            })
            .collect(java.util.stream.Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", locationData);
        return result;
    }

    @GetMapping("/stats/monthly")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getMonthlyStats() {
        Map<String, Object> monthlyStats = ticketService.getMonthlyStats();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", monthlyStats);
        return result;
    }

    @GetMapping("/stats/repairman-rating")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getRepairmanRatingStats() {
        List<com.ligong.reportingcenter.dto.RepairmanRatingStatsDto> ratingStats = ticketService.getRepairmanRatingStats();
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
            .collect(java.util.stream.Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", ratingData);
        return result;
    }

    // 新增：获取平均处理时间统计
    @GetMapping("/stats/processing-time")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getProcessingTimeStats() {
        Map<String, Object> processingTimeStats = ticketService.getAverageProcessingTime();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", processingTimeStats);
        return result;
    }

    @GetMapping("/stats/sla")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getSlaOverview() {
        Map<String, Object> slaOverview = ticketService.getSlaOverview();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", slaOverview);
        return result;
    }

    @GetMapping("/stats/hotspot")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getHotspotAnalysis() {
        Map<String, Object> hotspotAnalysis = ticketService.getHotspotAnalysis();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", hotspotAnalysis);
        return result;
    }
}