package com.qiyun.repairservice;

import com.qiyun.repairservice.domain.entity.Rating;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.repository.RatingRepository;
import com.qiyun.repairservice.repository.TicketRepository;
import com.qiyun.repairservice.repository.UserReferenceRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.time.LocalDateTime;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Repair服务内部接口安全测试
 * Mock掉数据库依赖的服务层，仅测试安全配置
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalRepairSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.qiyun.repairservice.service.TicketService ticketService;

    @MockBean
    private com.qiyun.repairservice.service.FacilityHealthService facilityHealthService;

    @Autowired
    private UserReferenceRepository userReferenceRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private RatingRepository ratingRepository;

    private String adminToken;
    private String studentToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        // Mock所有可能导致数据库查询的方法
        when(ticketService.getCategoryStatsFromView()).thenReturn(java.util.List.of());
        when(ticketService.getLocationStats()).thenReturn(java.util.List.of());
        when(ticketService.getMonthlyStats()).thenReturn(Map.of());
        when(ticketService.getRepairmanRatingStats()).thenReturn(java.util.List.of());
        when(ticketService.getAverageProcessingTime()).thenReturn(Map.of());
        when(ticketService.getSlaOverview()).thenReturn(Map.of());
        when(ticketService.getHotspotAnalysis()).thenReturn(Map.of());
        when(facilityHealthService.getFacilityHealthIndex()).thenReturn(Map.of());

        adminToken = generateToken("admin01", "ADMIN");
        studentToken = generateToken("student01", "STUDENT");
        staffToken = generateToken("staff01", "STAFF");
    }

    private String generateToken(String userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);

        return Jwts.builder()
            .setClaims(claims)
            .setSubject(userId)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 86400000))
            .signWith(Keys.hmacShaKeyFor("test-jwt-secret-key-for-repair-service-testing-only-minimum-256-bits-required".getBytes()), SignatureAlgorithm.HS256)
            .compact();
    }

    // ========== 内部接口需要ADMIN权限 ==========

    @Test
    void unauthenticatedCannotAccessInternalStats() throws Exception {
        mockMvc.perform(get("/internal/repair/stats/status"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotAccessInternalStats() throws Exception {
        mockMvc.perform(get("/internal/repair/stats/status")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotAccessInternalStats() throws Exception {
        mockMvc.perform(get("/internal/repair/stats/status")
                .header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessInternalStatusStats() throws Exception {
        mockMvc.perform(get("/internal/repair/stats/status")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanAccessInternalCategoryStats() throws Exception {
        mockMvc.perform(get("/internal/repair/stats/category")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanAccessInternalLocationStats() throws Exception {
        mockMvc.perform(get("/internal/repair/stats/location")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanAccessInternalMonthlyStats() throws Exception {
        mockMvc.perform(get("/internal/repair/stats/monthly")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanAccessInternalFacilityHealth() throws Exception {
        mockMvc.perform(get("/internal/repair/stats/facility-health")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void studentCannotCorrectAiAnalysis() throws Exception {
        mockMvc.perform(put("/api/admin/repair-orders/1/ai-analysis/correction")
                .header("Authorization", "Bearer " + studentToken)
                .contentType("application/json")
                .content("{\"categoryKey\":\"manual\",\"reason\":\"reviewed\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotCorrectAiAnalysis() throws Exception {
        mockMvc.perform(put("/api/admin/repair-orders/1/ai-analysis/correction")
                .header("Authorization", "Bearer " + staffToken)
                .contentType("application/json")
                .content("{\"categoryKey\":\"manual\",\"reason\":\"reviewed\"}"))
            .andExpect(status().isForbidden());
    }

    // ========== 内部评价接口 ==========

    @Test
    void unauthenticatedCannotAccessInternalFeedbacks() throws Exception {
        mockMvc.perform(get("/internal/repair/feedbacks"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotAccessInternalFeedbacks() throws Exception {
        mockMvc.perform(get("/internal/repair/feedbacks")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessInternalFeedbacks() throws Exception {
        mockMvc.perform(get("/internal/repair/feedbacks")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void negativeFeedbackFilterIncludesLowRatingFeedbacks() throws Exception {
        Rating rating = createRating(2, "POSITIVE");

        mockMvc.perform(get("/internal/repair/feedbacks")
                .param("sentiment", "NEGATIVE")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.sentimentCounts.NEGATIVE").value(1))
            .andExpect(jsonPath("$.data.list[0].ratingId").value(rating.getRatingId()));
    }

    private Rating createRating(int score, String sentiment) {
        UserReference student = createUser("negative-filter-student", "Negative Filter Student", UserRole.STUDENT);
        UserReference staff = createUser("negative-filter-staff", "Negative Filter Staff", UserRole.STAFF);

        RepairTicket ticket = new RepairTicket();
        ticket.setStudent(student);
        ticket.setStaff(staff);
        ticket.setStatus(TicketStatus.FEEDBACKED);
        ticket.setLocationText("Dorm A");
        ticket.setDescription("Test ticket");
        ticket.setCompletedAt(LocalDateTime.now().minusHours(1));
        ticket.setStudentConfirmedAt(LocalDateTime.now().minusMinutes(30));
        ticket = ticketRepository.save(ticket);

        Rating rating = new Rating();
        rating.setTicket(ticket);
        rating.setStudent(student);
        rating.setStaff(staff);
        rating.setScore(score);
        rating.setComment("Low score should be treated as negative");
        rating.setSentiment(sentiment);
        rating.setRatedAt(LocalDateTime.now());
        rating.setAnonymous(false);
        return ratingRepository.save(rating);
    }

    private UserReference createUser(String id, String name, UserRole role) {
        return userReferenceRepository.findById(id).orElseGet(() -> {
            UserReference user = new UserReference();
            user.setUserId(id);
            user.setNickname(name);
            user.setRole(role);
            user.setIsActive(true);
            return userReferenceRepository.save(user);
        });
    }
}
