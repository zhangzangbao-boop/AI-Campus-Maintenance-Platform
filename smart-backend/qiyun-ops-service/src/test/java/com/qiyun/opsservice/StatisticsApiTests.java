package com.qiyun.opsservice;

import com.qiyun.feign.client.AiServiceClient;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统计API安全测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StatisticsApiTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepairServiceClient repairServiceClient;

    @MockBean
    private AiServiceClient aiServiceClient;

    private String adminToken;
    private String studentToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        // Mock RepairServiceClient
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("code", 200);
        mockResponse.put("message", "获取成功");
        mockResponse.put("data", java.util.List.of());

        when(repairServiceClient.getStatusStats(any())).thenReturn(mockResponse);
        when(repairServiceClient.getCategoryStats(any())).thenReturn(mockResponse);
        when(repairServiceClient.getLocationStats(any())).thenReturn(mockResponse);
        when(repairServiceClient.getMonthlyStats(any())).thenReturn(mockResponse);
        when(repairServiceClient.getRepairmanRatingStats(any())).thenReturn(mockResponse);
        when(repairServiceClient.getProcessingTimeStats(any())).thenReturn(mockResponse);
        when(repairServiceClient.getSlaOverview(any())).thenReturn(mockResponse);
        when(repairServiceClient.getHotspotAnalysis(any())).thenReturn(mockResponse);
        when(repairServiceClient.getFacilityHealth(any())).thenReturn(mockResponse);
        when(repairServiceClient.getFaultTrendStats(any(), anyInt())).thenReturn(faultTrendResponse());
        doThrow(new RuntimeException("ai down")).when(aiServiceClient).analyzeTicket(any());

        Map<String, Object> feedbacksResponse = new HashMap<>();
        feedbacksResponse.put("code", 200);
        feedbacksResponse.put("message", "获取成功");
        feedbacksResponse.put("data", Map.of("list", java.util.List.of(), "total", 0));
        when(repairServiceClient.getFeedbacks(any(), anyInt(), anyInt(), any(), any(), any(), any(), any())).thenReturn(feedbacksResponse);
        when(repairServiceClient.getFeedbacksCount(any())).thenReturn(0L);

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
    void adminCanGetStatusStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/status")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void studentCannotGetStatusStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/status")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotGetStatusStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/status")
                .header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotGetStatusStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/status"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanGetCategoryStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/category")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void studentCannotGetCategoryStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/category")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanGetLocationStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/location")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanGetMonthlyStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/monthly")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanGetRepairmanRatingStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/repairman-rating")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanGetProcessingTimeStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/processing-time")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanGetSlaOverview() throws Exception {
        mockMvc.perform(get("/api/admin/stats/sla")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanGetHotspotAnalysis() throws Exception {
        mockMvc.perform(get("/api/admin/stats/hotspot")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanGetFacilityHealth() throws Exception {
        mockMvc.perform(get("/api/admin/stats/facility-health")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanGetFaultTrends() throws Exception {
        mockMvc.perform(get("/api/admin/stats/fault-trends")
            .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.trends.length()").value(2));
    }

    @Test
    void adminCanRefreshFaultTrendsWithAiFallback() throws Exception {
        mockMvc.perform(post("/api/admin/stats/fault-trends/refresh")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alerts.length()").value(1))
            .andExpect(jsonPath("$.data.alerts[0].location").value("一号宿舍"))
            .andExpect(jsonPath("$.data.alerts[0].periodDays").value(7));
    }

    @Test
    void studentCannotGetFaultTrends() throws Exception {
        mockMvc.perform(get("/api/admin/stats/fault-trends")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotGetFacilityHealth() throws Exception {
        mockMvc.perform(get("/api/admin/stats/facility-health")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotGetFacilityHealth() throws Exception {
        mockMvc.perform(get("/api/admin/stats/facility-health"))
            .andExpect(status().isUnauthorized());
    }

    private Map<String, Object> faultTrendResponse() {
        Map<String, Object> item = new HashMap<>();
        item.put("location", "一号宿舍");
        item.put("category", "空调故障");
        item.put("periodDays", 7);
        item.put("ticketCount", 4);
        item.put("previousCount", 1);
        item.put("growthRate", 300.0);

        Map<String, Object> data = new HashMap<>();
        data.put("periodDays", 7);
        data.put("items", java.util.List.of(item));

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "获取成功");
        response.put("data", data);
        return response;
    }
}
