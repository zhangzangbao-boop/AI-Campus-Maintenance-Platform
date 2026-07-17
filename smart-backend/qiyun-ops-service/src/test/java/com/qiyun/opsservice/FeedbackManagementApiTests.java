package com.qiyun.opsservice;

import com.qiyun.feign.client.RepairServiceClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评价管理API安全测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedbackManagementApiTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepairServiceClient repairServiceClient;

    private String adminToken;
    private String studentToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        Map<String, Object> feedbacksResponse = new HashMap<>();
        feedbacksResponse.put("code", 200);
        feedbacksResponse.put("message", "获取成功");
        feedbacksResponse.put("data", Map.of("list", java.util.List.of(), "total", 0));
        when(repairServiceClient.getFeedbacks(any(), anyInt(), anyInt(), any(), any())).thenReturn(feedbacksResponse);

        adminToken = generateToken("admin01", "ADMIN");
        studentToken = generateToken("student01", "STUDENT");
        staffToken = generateToken("staff01", "STAFF");
    }

    private String generateToken(String userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", java.util.List.of(role));  // JwtUtil expects "roles" (plural)

        return Jwts.builder()
            .setClaims(claims)
            .setSubject(userId)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 86400000))
            .signWith(Keys.hmacShaKeyFor("test-jwt-secret-key-for-ops-service-testing-only-minimum-256-bits-required".getBytes()), SignatureAlgorithm.HS256)
            .compact();
    }

    @Test
    void adminCanGetFeedbacks() throws Exception {
        mockMvc.perform(get("/api/admin/feedbacks")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanGetFeedbacksWithPaging() throws Exception {
        mockMvc.perform(get("/api/admin/feedbacks")
                .param("page", "0")
                .param("size", "10")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanGetFeedbacksWithLowRatingFilter() throws Exception {
        mockMvc.perform(get("/api/admin/feedbacks")
                .param("lowRating", "true")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanGetNegativeFeedbacksFilter() throws Exception {
        mockMvc.perform(get("/api/admin/feedbacks")
                .param("sentiment", "NEGATIVE")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void studentCannotGetFeedbacks() throws Exception {
        mockMvc.perform(get("/api/admin/feedbacks")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotGetFeedbacks() throws Exception {
        mockMvc.perform(get("/api/admin/feedbacks")
                .header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotGetFeedbacks() throws Exception {
        mockMvc.perform(get("/api/admin/feedbacks"))
            .andExpect(status().isUnauthorized());
    }
}