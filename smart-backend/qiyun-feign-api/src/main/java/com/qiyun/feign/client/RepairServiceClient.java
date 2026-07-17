package com.qiyun.feign.client;

import com.qiyun.feign.dto.FeedbackFollowUpUpdateRequest;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Repair 服务 Feign 客户端
 * 用于ops-service从repair-service获取统计数据
 *
 * 注意：内部接口路径为 /internal/repair/**，不经过Gateway
 */
@FeignClient(name = "qiyun-repair-service", path = "/internal/repair")
public interface RepairServiceClient {

    /**
     * 获取工单状态统计
     */
    @GetMapping("/stats/status")
    Map<String, Object> getStatusStats(@RequestHeader(value = "Authorization", required = false) String authorization);

    /**
     * 获取分类统计
     */
    @GetMapping("/stats/category")
    Map<String, Object> getCategoryStats(@RequestHeader(value = "Authorization", required = false) String authorization);

    /**
     * 获取地点统计
     */
    @GetMapping("/stats/location")
    Map<String, Object> getLocationStats(@RequestHeader(value = "Authorization", required = false) String authorization);

    /**
     * 获取月度统计
     */
    @GetMapping("/stats/monthly")
    Map<String, Object> getMonthlyStats(@RequestHeader(value = "Authorization", required = false) String authorization);

    /**
     * 获取维修工评分统计
     */
    @GetMapping("/stats/repairman-rating")
    Map<String, Object> getRepairmanRatingStats(@RequestHeader(value = "Authorization", required = false) String authorization);

    /**
     * 获取平均处理时间
     */
    @GetMapping("/stats/processing-time")
    Map<String, Object> getProcessingTimeStats(@RequestHeader(value = "Authorization", required = false) String authorization);

    /**
     * 获取SLA概览
     */
    @GetMapping("/stats/sla")
    Map<String, Object> getSlaOverview(@RequestHeader(value = "Authorization", required = false) String authorization);

    /**
     * 获取热点分析
     */
    @GetMapping("/stats/hotspot")
    Map<String, Object> getHotspotAnalysis(@RequestHeader(value = "Authorization", required = false) String authorization);

    /**
     * 获取设施健康数据
     */
    @GetMapping("/stats/facility-health")
    Map<String, Object> getFacilityHealth(@RequestHeader(value = "Authorization", required = false) String authorization);

    /**
     * 获取评价列表（分页）
     */
    @GetMapping("/feedbacks")
    Map<String, Object> getFeedbacks(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "10") int size,
        @RequestParam(value = "lowRating", required = false) Boolean lowRating,
        @RequestParam(value = "sentiment", required = false) String sentiment,
        @RequestParam(value = "followUpStatus", required = false) String followUpStatus
    );

    @PutMapping("/feedbacks/{ratingId}/follow-up")
    Map<String, Object> updateFeedbackFollowUp(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable("ratingId") Long ratingId,
        @RequestBody FeedbackFollowUpUpdateRequest request
    );

    /**
     * 删除评价
     */
    @GetMapping("/feedbacks/count")
    Long getFeedbacksCount(@RequestHeader(value = "Authorization", required = false) String authorization);
}
