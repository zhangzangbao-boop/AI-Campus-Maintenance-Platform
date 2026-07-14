package com.qiyun.userservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.userservice.domain.enums.UserRole;
import com.qiyun.userservice.dto.request.LoginRequest;
import com.qiyun.userservice.dto.request.UserRegisterRequest;
import com.qiyun.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT认证过滤器测试
 * 验证禁用用户Token认证行为
 */
@SpringBootTest(classes = {UserServiceApplication.class, JwtAuthenticationFilterTests.TestController.class})
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class JwtAuthenticationFilterTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    private static int userCounter = 0;

    private String generateUniqueUserId() {
        return String.format("TEST%d%04d", System.currentTimeMillis() % 10000, ++userCounter);
    }

    @BeforeEach
    void setUp() {
        userCounter++;
    }

    @Test
    @DisplayName("启用用户的合法Token能够通过过滤器认证")
    void enabledUser_validToken_authenticated() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "启用用户",
                "13800138020",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest(userId, "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).path("token").asText();

        // 使用Token访问需要认证的测试端点
        mockMvc.perform(get("/api/test/authenticated")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).startsWith("authenticated:" + userId);
                });
    }

    @Test
    @DisplayName("禁用用户的旧Token不能通过过滤器认证")
    void disabledUser_oldToken_notAuthenticated() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "禁用用户",
                "13800138021",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest(userId, "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).path("token").asText();

        // 禁用用户
        var user = userService.loadActiveUser(userId);
        user.setIsActive(false);

        // 使用旧Token访问需要认证的端点 - 应返回401
        mockMvc.perform(get("/api/test/authenticated")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("篡改Token不能通过认证")
    void tamperedToken_notAuthenticated() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "篡改测试用户",
                "13800138022",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest(userId, "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).path("token").asText();

        // 篡改Token
        String tamperedToken = token.substring(0, 20) + "X" + token.substring(21);

        mockMvc.perform(get("/api/test/authenticated")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("不携带Token时返回401")
    void noToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/test/authenticated"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("重新启用后未过期Token可以恢复认证")
    void reEnabledUser_validToken_authenticated() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "重新启用用户",
                "13800138023",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest(userId, "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).path("token").asText();

        // 禁用
        var user = userService.loadActiveUser(userId);
        user.setIsActive(false);

        // 验证禁用后无法认证
        mockMvc.perform(get("/api/test/authenticated")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // 重新启用
        user.setIsActive(true);

        // 验证Token恢复认证
        mockMvc.perform(get("/api/test/authenticated")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).startsWith("authenticated:" + userId);
                });
    }

    @Test
    @DisplayName("格式错误的Token返回401")
    void malformedToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/test/authenticated")
                        .header("Authorization", "Bearer invalid.token.format"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("空Token返回401")
    void emptyToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/test/authenticated")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 测试专用Controller，用于验证JWT过滤器行为
     * 仅用于测试环境，不参与生产业务
     */
    @RestController
    @RequestMapping("/api/test")
    static class TestController {

        @GetMapping("/authenticated")
        public ResponseEntity<String> getAuthenticatedUser(@AuthenticationPrincipal UserDetails userDetails) {
            if (userDetails != null) {
                return ResponseEntity.ok("authenticated:" + userDetails.getUsername());
            }
            return ResponseEntity.ok("anonymous");
        }
    }
}