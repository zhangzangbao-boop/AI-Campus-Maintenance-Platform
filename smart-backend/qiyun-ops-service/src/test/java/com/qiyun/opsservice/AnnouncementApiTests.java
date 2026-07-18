package com.qiyun.opsservice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.feign.client.RepairServiceClient;
import com.qiyun.opsservice.domain.entity.Announcement;
import com.qiyun.opsservice.domain.entity.UserReference;
import com.qiyun.opsservice.domain.enums.AnnouncementPriority;
import com.qiyun.opsservice.domain.enums.AnnouncementStatus;
import com.qiyun.opsservice.domain.enums.AnnouncementType;
import com.qiyun.opsservice.domain.enums.UserRole;
import com.qiyun.opsservice.dto.NotificationDto;
import com.qiyun.opsservice.dto.request.AnnouncementRequest;
import com.qiyun.opsservice.repository.AnnouncementRepository;
import com.qiyun.opsservice.repository.UserReferenceRepository;
import com.qiyun.opsservice.websocket.WebSocketPushService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.time.LocalDateTime;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnnouncementApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private UserReferenceRepository userReferenceRepository;

    @MockBean
    private RepairServiceClient repairServiceClient;

    @MockBean
    private WebSocketPushService webSocketPushService;

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        announcementRepository.deleteAll();
        adminToken = generateToken("admin01", "ADMIN");
        studentToken = generateToken("student01", "STUDENT");
        createUser("admin01", UserRole.ADMIN);
        createUser("student01", UserRole.STUDENT);
        createUser("staff01", UserRole.STAFF);
    }

    @Test
    void adminCanCreateAndPublishAnnouncement() throws Exception {
        AnnouncementRequest request = new AnnouncementRequest(
            "停水通知",
            "今晚十点宿舍区停水检修",
            AnnouncementType.MAINTENANCE,
            AnnouncementPriority.HIGH,
            null,
            LocalDateTime.now().plusDays(1),
            true
        );

        String response = mockMvc.perform(post("/api/admin/announcements")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(post("/api/admin/announcements/{id}/publish", id)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.pinned").value(true));
    }

    @Test
    void studentCannotManageAnnouncements() throws Exception {
        mockMvc.perform(get("/api/admin/announcements")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void visibleListOnlyContainsPublishedAndUnexpiredAnnouncements() throws Exception {
        saveAnnouncement("草稿", AnnouncementStatus.DRAFT, null, null, false);
        saveAnnouncement("过期", AnnouncementStatus.PUBLISHED, LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), false);
        saveAnnouncement("可见", AnnouncementStatus.PUBLISHED, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1), true);

        mockMvc.perform(get("/api/announcements")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("可见"));
    }

    @Test
    void publishStillSucceedsWhenWebSocketPushFails() throws Exception {
        doThrow(new RuntimeException("push down"))
            .when(webSocketPushService).pushNotificationToMany(any(), any(NotificationDto.class));
        Announcement announcement = saveAnnouncement("推送降级", AnnouncementStatus.DRAFT, null, null, false);

        mockMvc.perform(post("/api/admin/announcements/{id}/publish", announcement.getAnnouncementId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    private Announcement saveAnnouncement(String title, AnnouncementStatus status, LocalDateTime publishTime,
                                          LocalDateTime expireTime, boolean pinned) {
        Announcement item = new Announcement();
        item.setTitle(title);
        item.setContent(title + "内容");
        item.setType(AnnouncementType.GENERAL);
        item.setPriority(AnnouncementPriority.NORMAL);
        item.setStatus(status);
        item.setPublishTime(publishTime);
        item.setExpireTime(expireTime);
        item.setPinned(pinned);
        return announcementRepository.save(item);
    }

    private void createUser(String userId, UserRole role) {
        UserReference user = userReferenceRepository.findById(userId).orElseGet(UserReference::new);
        user.setUserId(userId);
        user.setNickname(userId);
        user.setRole(role);
        user.setIsActive(true);
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now());
        }
        userReferenceRepository.save(user);
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
