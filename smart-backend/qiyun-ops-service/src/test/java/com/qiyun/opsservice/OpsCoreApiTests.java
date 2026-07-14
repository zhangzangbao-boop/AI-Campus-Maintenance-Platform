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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ops服务核心API测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpsCoreApiTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepairServiceClient repairServiceClient;

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        adminToken = generateToken("admin01", "ADMIN");
        studentToken = generateToken("student01", "STUDENT");
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
    void userCanGetNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void unauthenticatedCannotGetNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanGetAuditLogs() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void studentCannotGetAuditLogs() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotGetAuditLogs() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanGetSystemConfig() throws Exception {
        mockMvc.perform(get("/api/admin/system-config")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void studentCannotGetSystemConfig() throws Exception {
        mockMvc.perform(get("/api/admin/system-config")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotGetSystemConfig() throws Exception {
        mockMvc.perform(get("/api/admin/system-config"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanSearchKnowledgeBase() throws Exception {
        mockMvc.perform(get("/api/knowledge-base/search")
                .param("keyword", "空调")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void userCanGetKnowledgeBaseRecommend() throws Exception {
        mockMvc.perform(get("/api/knowledge-base/recommend")
                .param("category", "空调故障")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedCannotAddKnowledgeBase() throws Exception {
        mockMvc.perform(put("/api/knowledge-base")
                .contentType("application/json")
                .content("{\"title\":\"test\",\"content\":\"test\"}"))
            .andExpect(status().isUnauthorized());
    }
}