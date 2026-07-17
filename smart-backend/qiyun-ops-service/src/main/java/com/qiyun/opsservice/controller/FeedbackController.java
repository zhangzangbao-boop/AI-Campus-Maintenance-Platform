package com.qiyun.opsservice.controller;

import com.qiyun.feign.dto.FeedbackFollowUpUpdateRequest;
import com.qiyun.opsservice.service.FeedbackService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping("/feedbacks")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> listFeedbacks(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "lowRating", required = false) Boolean lowRating,
            @RequestParam(value = "sentiment", required = false) String sentiment,
            @RequestParam(value = "followUpStatus", required = false) String followUpStatus) {
        log.info(
            "ADMIN list feedbacks: page={}, size={}, lowRating={}, sentiment={}, followUpStatus={}",
            page, size, lowRating, sentiment, followUpStatus
        );
        return feedbackService.getFeedbacks(authorization, page, size, lowRating, sentiment, followUpStatus);
    }

    @PutMapping("/feedbacks/{ratingId}/follow-up")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> updateFeedbackFollowUp(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long ratingId,
            @RequestBody FeedbackFollowUpUpdateRequest request) {
        FeedbackFollowUpUpdateRequest safeRequest = new FeedbackFollowUpUpdateRequest(
            currentUserId(),
            request == null ? null : request.status(),
            request == null ? null : request.note()
        );
        return feedbackService.updateFeedbackFollowUp(authorization, ratingId, safeRequest);
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "" : authentication.getName();
    }
}
