package com.qiyun.opsservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.feign.client.RepairServiceClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 统计服务 - 通过Feign调用repair-service内部接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final RepairServiceClient repairServiceClient;

    /**
     * 获取工单状态统计
     */
    public Map<String, Object> getStatusStats(String authorization) {
        try {
            log.debug("调用repair-service获取状态统计");
            Map<String, Object> result = repairServiceClient.getStatusStats(authorization);
            log.debug("状态统计结果: {}", result);
            return result;
        } catch (Exception e) {
            log.error("调用repair-service状态统计失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "统计服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 获取分类统计
     */
    public Map<String, Object> getCategoryStats(String authorization) {
        try {
            log.debug("调用repair-service获取分类统计");
            return repairServiceClient.getCategoryStats(authorization);
        } catch (Exception e) {
            log.error("调用repair-service分类统计失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "统计服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 获取地点统计
     */
    public Map<String, Object> getLocationStats(String authorization) {
        try {
            log.debug("调用repair-service获取地点统计");
            return repairServiceClient.getLocationStats(authorization);
        } catch (Exception e) {
            log.error("调用repair-service地点统计失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "统计服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 获取月度统计
     */
    public Map<String, Object> getMonthlyStats(String authorization) {
        try {
            log.debug("调用repair-service获取月度统计");
            return repairServiceClient.getMonthlyStats(authorization);
        } catch (Exception e) {
            log.error("调用repair-service月度统计失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "统计服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 获取维修工评分统计
     */
    public Map<String, Object> getRepairmanRatingStats(String authorization) {
        try {
            log.debug("调用repair-service获取维修工评分统计");
            return repairServiceClient.getRepairmanRatingStats(authorization);
        } catch (Exception e) {
            log.error("调用repair-service维修工评分统计失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "统计服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 获取平均处理时间
     */
    public Map<String, Object> getProcessingTimeStats(String authorization) {
        try {
            log.debug("调用repair-service获取平均处理时间");
            return repairServiceClient.getProcessingTimeStats(authorization);
        } catch (Exception e) {
            log.error("调用repair-service平均处理时间失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "统计服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 获取SLA概览
     */
    public Map<String, Object> getSlaOverview(String authorization) {
        try {
            log.debug("调用repair-service获取SLA概览");
            return repairServiceClient.getSlaOverview(authorization);
        } catch (Exception e) {
            log.error("调用repair-service SLA概览失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "统计服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 获取热点分析
     */
    public Map<String, Object> getHotspotAnalysis(String authorization) {
        try {
            log.debug("调用repair-service获取热点分析");
            return repairServiceClient.getHotspotAnalysis(authorization);
        } catch (Exception e) {
            log.error("调用repair-service热点分析失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "统计服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 获取设施健康数据
     */
    public Map<String, Object> getFacilityHealth(String authorization) {
        try {
            log.debug("调用repair-service获取设施健康数据");
            return repairServiceClient.getFacilityHealth(authorization);
        } catch (Exception e) {
            log.error("调用repair-service设施健康失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "统计服务暂时不可用，请稍后重试");
        }
    }
}