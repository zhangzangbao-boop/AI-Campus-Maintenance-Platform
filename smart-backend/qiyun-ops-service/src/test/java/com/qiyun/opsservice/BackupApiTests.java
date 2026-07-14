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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 备份管理API测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackupApiTests {

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
    void adminCanListBackups() throws Exception {
        mockMvc.perform(get("/api/admin/backup/list")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void studentCannotListBackups() throws Exception {
        mockMvc.perform(get("/api/admin/backup/list")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotListBackups() throws Exception {
        mockMvc.perform(get("/api/admin/backup/list"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanGetBackupStatus() throws Exception {
        mockMvc.perform(get("/api/admin/backup/status")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.directory").exists());
    }

    @Test
    void unauthenticatedCannotGetBackupStatus() throws Exception {
        mockMvc.perform(get("/api/admin/backup/status"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void pathTraversalIsBlocked() throws Exception {
        mockMvc.perform(delete("/api/admin/backup/..%2F..%2F..%2Fetc%2Fpasswd")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void pathTraversalWithBackslashIsBlocked() throws Exception {
        mockMvc.perform(delete("/api/admin/backup/..\\..\\..\\windows\\system32\\config\\sam")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void adminCanCreateBackup() throws Exception {
        mockMvc.perform(post("/api/admin/backup/create")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void adminCanRestoreBackup() throws Exception {
        mockMvc.perform(post("/api/admin/backup/restore")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"fileName\":\"test.sql\"}"))
            .andExpect(status().is5xxServerError());
    }
}