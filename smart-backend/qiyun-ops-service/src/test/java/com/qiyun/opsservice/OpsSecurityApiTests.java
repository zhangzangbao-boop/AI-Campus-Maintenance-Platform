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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ops服务安全API测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpsSecurityApiTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepairServiceClient repairServiceClient;

    private String adminToken;
    private String studentToken;
    private String staffToken;
    private String disabledUserToken;

    @BeforeEach
    void setUp() {
        adminToken = generateToken("admin01", "ADMIN");
        studentToken = generateToken("student01", "STUDENT");
        staffToken = generateToken("staff01", "STAFF");
        disabledUserToken = generateToken("disabled01", "DISABLED");
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
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void adminPathRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotAccessAdminPath() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotAccessAdminPath() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAdminPath() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void statsPathRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/stats/status"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotAccessStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/status")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotAccessStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/status")
                .header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void feedbacksPathRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/feedbacks"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotAccessFeedbacks() throws Exception {
        mockMvc.perform(get("/api/admin/feedbacks")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotAccessFeedbacks() throws Exception {
        mockMvc.perform(get("/api/admin/feedbacks")
                .header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void disabledUserRoleNotAllowed() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + disabledUserToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeBaseSearchIsPublic() throws Exception {
        mockMvc.perform(get("/api/knowledge-base/search")
                .param("keyword", "test"))
            .andExpect(status().isOk());
    }

    @Test
    void knowledgeBaseRecommendIsPublic() throws Exception {
        mockMvc.perform(get("/api/knowledge-base/recommend")
                .param("category", "test"))
            .andExpect(status().isOk());
    }
}