package com.qiyun.opsservice.controller;

import com.qiyun.opsservice.service.StatisticsService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计分析控制器
 * 所有统计接口仅限ADMIN访问
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/stats")
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 获取工单状态统计
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getStatusStats(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        log.info("ADMIN请求获取状态统计");
        return statisticsService.getStatusStats(authorization);
    }

    /**
     * 获取分类统计
     */
    @GetMapping("/category")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getCategoryStats(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        log.info("ADMIN请求获取分类统计");
        return statisticsService.getCategoryStats(authorization);
    }

    /**
     * 获取地点统计
     */
    @GetMapping("/location")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getLocationStats(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        log.info("ADMIN请求获取地点统计");
        return statisticsService.getLocationStats(authorization);
    }

    /**
     * 获取月度统计
     */
    @GetMapping("/monthly")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getMonthlyStats(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        log.info("ADMIN请求获取月度统计");
        return statisticsService.getMonthlyStats(authorization);
    }

    /**
     * 获取维修工评分统计
     */
    @GetMapping("/repairman-rating")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getRepairmanRatingStats(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        log.info("ADMIN请求获取维修工评分统计");
        return statisticsService.getRepairmanRatingStats(authorization);
    }

    /**
     * 获取平均处理时间
     */
    @GetMapping("/processing-time")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getProcessingTimeStats(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        log.info("ADMIN请求获取平均处理时间");
        return statisticsService.getProcessingTimeStats(authorization);
    }

    /**
     * 获取SLA概览
     */
    @GetMapping("/sla")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getSlaOverview(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        log.info("ADMIN请求获取SLA概览");
        return statisticsService.getSlaOverview(authorization);
    }

    /**
     * 获取热点分析
     */
    @GetMapping("/hotspot")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getHotspotAnalysis(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        log.info("ADMIN请求获取热点分析");
        return statisticsService.getHotspotAnalysis(authorization);
    }

    /**
     * 获取设施健康数据
     */
    @GetMapping("/facility-health")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getFacilityHealth(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        log.info("ADMIN请求获取设施健康数据");
        return statisticsService.getFacilityHealth(authorization);
    }
}