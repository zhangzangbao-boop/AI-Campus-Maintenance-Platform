package com.qiyun.opsservice.controller;

import com.qiyun.opsservice.service.FeedbackService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价管理控制器
 * 所有评价管理接口仅限ADMIN访问
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 获取评价列表（分页）
     */
    @GetMapping("/feedbacks")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> listFeedbacks(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "lowRating", required = false) Boolean lowRating) {
        log.info("ADMIN请求获取评价列表: page={}, size={}, lowRating={}", page, size, lowRating);
        return feedbackService.getFeedbacks(authorization, page, size, lowRating);
    }
}