package com.qiyun.opsservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.feign.client.RepairServiceClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 评价管理服务 - 通过Feign调用repair-service内部接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final RepairServiceClient repairServiceClient;

    /**
     * 获取评价列表（分页）
     */
    public Map<String, Object> getFeedbacks(String authorization, int page, int size, Boolean lowRating, String sentiment) {
        try {
            log.debug("调用repair-service获取评价列表: page={}, size={}, lowRating={}, sentiment={}", page, size, lowRating, sentiment);
            Map<String, Object> result = repairServiceClient.getFeedbacks(authorization, page, size, lowRating, sentiment);
            log.debug("评价列表结果: total={}", result != null && result.get("data") != null ?
                ((Map<?, ?>) result.get("data")).get("total") : "unknown");
            return result;
        } catch (Exception e) {
            log.error("调用repair-service评价列表失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "评价服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 获取评价总数
     */
    public Long getFeedbacksCount(String authorization) {
        try {
            log.debug("调用repair-service获取评价总数");
            Long count = repairServiceClient.getFeedbacksCount(authorization);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("调用repair-service评价总数失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "评价服务暂时不可用，请稍后重试");
        }
    }
}