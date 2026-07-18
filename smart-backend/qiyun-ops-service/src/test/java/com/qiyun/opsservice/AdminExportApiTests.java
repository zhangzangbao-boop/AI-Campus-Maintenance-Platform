package com.qiyun.opsservice;

import com.qiyun.feign.client.AiServiceClient;
import com.qiyun.feign.client.RepairServiceClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminExportApiTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepairServiceClient repairServiceClient;

    @MockBean
    private AiServiceClient aiServiceClient;

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        when(repairServiceClient.getTicketsForExport(any(), any(), any(), any(), anyBoolean(), any(), any(), anyInt()))
            .thenReturn(Map.of("data", Map.of("list", List.of(Map.of(
                "ticketId", 1,
                "status", "WAITING_ACCEPT",
                "categoryName", "管道故障",
                "description", "漏水"
            )), "total", 1)));
        adminToken = generateToken("admin01", "ADMIN");
        studentToken = generateToken("student01", "STUDENT");
    }

    @Test
    void adminCanExportCsv() throws Exception {
        mockMvc.perform(get("/api/admin/export")
                .param("type", "tickets")
                .param("status", "pending")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(header().string("X-Export-Row-Count", "1"));
    }

    @Test
    void studentCannotExportCsv() throws Exception {
        mockMvc.perform(get("/api/admin/export")
                .param("type", "tickets")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    private String generateToken(String userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", List.of(role));
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(userId)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 86400000))
            .signWith(Keys.hmacShaKeyFor("test-jwt-secret-key-for-ops-service-testing-only-minimum-256-bits-required".getBytes()), SignatureAlgorithm.HS256)
            .compact();
    }
}
