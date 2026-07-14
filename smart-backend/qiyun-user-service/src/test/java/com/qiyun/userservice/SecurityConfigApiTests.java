package com.qiyun.userservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.userservice.domain.enums.UserRole;
import com.qiyun.userservice.dto.request.LoginRequest;
import com.qiyun.userservice.dto.request.UserRegisterRequest;
import com.qiyun.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * SecurityConfig权限测试
 * 验证路径匹配和权限规则正确配置
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class SecurityConfigApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    private static int userCounter = 0;

    private String generateUniqueUserId() {
        return String.format("SEC%d%04d", System.currentTimeMillis() % 10000, ++userCounter);
    }

    private String registerAndLogin(String userId, String password, String nickname, UserRole role) throws Exception {
        UserRegisterRequest registerRequest = new UserRegisterRequest(userId, password, nickname, "13800138000", role);
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest(userId, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).path("token").asText();
    }

    @BeforeEach
    void setUp() {
        userCounter++;
    }

    // ==================== /api/auth/** 权限测试 ====================

    @Nested
    @DisplayName("/api/auth/** 权限测试")
    class AuthPathTests {

        @Test
        @DisplayName("/api/auth/register允许匿名访问")
        void register_allowsAnonymous() throws Exception {
            UserRegisterRequest request = new UserRegisterRequest(
                    generateUniqueUserId(), "password123", "新用户", "13900138001", UserRole.STUDENT);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("/api/auth/login允许匿名访问")
        void login_allowsAnonymous() throws Exception {
            String userId = generateUniqueUserId();
            UserRegisterRequest registerRequest = new UserRegisterRequest(
                    userId, "password123", "用户", "13900138002", UserRole.STUDENT);
            userService.register(registerRequest);

            LoginRequest loginRequest = new LoginRequest(userId, "password123");
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk());
        }
    }

    // ==================== /api/users/** 权限测试 ====================

    @Nested
    @DisplayName("/api/users/** 权限测试")
    class UsersPathTests {

        @Test
        @DisplayName("/api/users/me需要认证")
        void usersMe_requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("/api/users/me/password需要认证")
        void changePassword_requiresAuthentication() throws Exception {
            String requestJson = "{\"oldPassword\":\"old\",\"newPassword\":\"newpassword\"}";

            mockMvc.perform(put("/api/users/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("/api/users仅ADMIN可访问")
        void listUsers_requiresAdmin() throws Exception {
            String studentToken = registerAndLogin(generateUniqueUserId(), "password123", "学生", UserRole.STUDENT);

            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + studentToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("/api/users/{userId}仅ADMIN可访问")
        void getUser_requiresAdmin() throws Exception {
            String studentToken = registerAndLogin(generateUniqueUserId(), "password123", "学生", UserRole.STUDENT);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "password123", "目标用户", "13900138003", UserRole.STUDENT);
            userService.register(createRequest);

            mockMvc.perform(get("/api/users/" + targetUserId)
                            .header("Authorization", "Bearer " + studentToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("/api/users/{userId}/activate仅ADMIN可访问")
        void activateUser_requiresAdmin() throws Exception {
            String studentToken = registerAndLogin(generateUniqueUserId(), "password123", "学生", UserRole.STUDENT);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "password123", "目标用户", "13900138004", UserRole.STUDENT);
            userService.register(createRequest);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/users/" + targetUserId + "/activate")
                            .header("Authorization", "Bearer " + studentToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("/api/users/{userId}/deactivate仅ADMIN可访问")
        void deactivateUser_requiresAdmin() throws Exception {
            String studentToken = registerAndLogin(generateUniqueUserId(), "password123", "学生", UserRole.STUDENT);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "password123", "目标用户", "13900138005", UserRole.STUDENT);
            userService.register(createRequest);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/users/" + targetUserId + "/deactivate")
                            .header("Authorization", "Bearer " + studentToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("/api/users/me不会被错误识别成动态userId的管理员接口")
        void usersMe_notMisidentifiedAsUserId() throws Exception {
            // 这个测试验证路径匹配顺序正确
            // /api/users/me 应该优先于 /api/users/{userId} 匹配
            String studentToken = registerAndLogin(generateUniqueUserId(), "password123", "学生", UserRole.STUDENT);

            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + studentToken))
                    .andExpect(status().isOk());
        }
    }

    // ==================== /api/admin/users/** 权限测试 ====================

    @Nested
    @DisplayName("/api/admin/users/** 权限测试")
    class AdminUsersPathTests {

        @Test
        @DisplayName("/api/admin/users仅ADMIN可访问")
        void adminUsers_requiresAdmin() throws Exception {
            String studentToken = registerAndLogin(generateUniqueUserId(), "password123", "学生", UserRole.STUDENT);
            String staffToken = registerAndLogin(generateUniqueUserId(), "password123", "维修工", UserRole.STAFF);

            mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer " + studentToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN可以访问/api/admin/users")
        void adminUsers_adminAccess_success() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);

            mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }

    // ==================== /actuator/** 权限测试 ====================

    @Nested
    @DisplayName("/actuator/** 权限测试")
    class ActuatorPathTests {

        @Test
        @DisplayName("/actuator/health允许匿名访问")
        void actuatorHealth_allowsAnonymous() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("/actuator/info允许匿名访问")
        void actuatorInfo_allowsAnonymous() throws Exception {
            mockMvc.perform(get("/actuator/info"))
                    .andExpect(status().isOk());
        }
    }
}