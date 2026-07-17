package com.qiyun.opsservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.feign.client.RepairServiceClient;
import com.qiyun.feign.dto.FeedbackFollowUpUpdateRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final RepairServiceClient repairServiceClient;

    public Map<String, Object> getFeedbacks(
            String authorization,
            int page,
            int size,
            Boolean lowRating,
            String sentiment,
            String followUpStatus) {
        try {
            log.debug(
                "List feedbacks: page={}, size={}, lowRating={}, sentiment={}, followUpStatus={}",
                page, size, lowRating, sentiment, followUpStatus
            );
            return repairServiceClient.getFeedbacks(authorization, page, size, lowRating, sentiment, followUpStatus);
        } catch (Exception e) {
            log.error("List feedbacks failed: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "Feedback service is temporarily unavailable, please try again later");
        }
    }

    public Map<String, Object> updateFeedbackFollowUp(
            String authorization,
            Long ratingId,
            FeedbackFollowUpUpdateRequest request) {
        try {
            return repairServiceClient.updateFeedbackFollowUp(authorization, ratingId, request);
        } catch (Exception e) {
            log.error("Update feedback follow-up failed: ratingId={}, error={}", ratingId, e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "Feedback service is temporarily unavailable, please try again later");
        }
    }

    public Long getFeedbacksCount(String authorization) {
        try {
            Long count = repairServiceClient.getFeedbacksCount(authorization);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Count feedbacks failed: {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "Feedback service is temporarily unavailable, please try again later");
        }
    }
}
