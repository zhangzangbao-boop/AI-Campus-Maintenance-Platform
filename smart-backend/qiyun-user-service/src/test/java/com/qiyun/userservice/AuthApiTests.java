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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class AuthApiTests {

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

    // ==================== 注册测试 ====================

    @Test
    @DisplayName("合法学生注册成功")
    void register_studentSuccess() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest request = new UserRegisterRequest(
                userId,
                "password123",
                "测试学生",
                "13800138001",
                UserRole.STUDENT
        );

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.nickname").value("测试学生"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("password").doesNotContain("Password");
    }

    // ==================== S0-01: 匿名注册角色限制测试 ====================

    @Test
    @DisplayName("匿名注册STUDENT成功")
    void register_studentRoleSuccess() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest request = new UserRegisterRequest(
                userId,
                "password123",
                "测试学生",
                "13800138001",
                UserRole.STUDENT
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    @DisplayName("匿名注册STAFF失败")
    void register_staffRole_fails() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest request = new UserRegisterRequest(
                userId,
                "password123",
                "测试维修工",
                "13800138002",
                UserRole.STAFF
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").value("公开注册仅允许创建学生账号"));

        LoginRequest loginRequest = new LoginRequest(userId, "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("匿名注册ADMIN失败")
    void register_adminRole_fails() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest request = new UserRegisterRequest(
                userId,
                "password123",
                "测试管理员",
                "13800138003",
                UserRole.ADMIN
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").value("公开注册仅允许创建学生账号"));

        LoginRequest loginRequest = new LoginRequest(userId, "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("重复用户编号注册失败")
    void register_duplicateUserId_fails() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest request1 = new UserRegisterRequest(
                userId,
                "password123",
                "第一个用户",
                "13800138004",
                UserRole.STUDENT
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        UserRegisterRequest request2 = new UserRegisterRequest(
                userId,
                "password456",
                "第二个用户",
                "13800138005",
                UserRole.STUDENT
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("缺少用户编号时返回参数错误")
    void register_missingUserId_fails() throws Exception {
        String invalidJson = """
                {
                    "password": "password123",
                    "nickname": "测试用户",
                    "contactPhone": "13800138006",
                    "role": "STUDENT"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("缺少密码时返回参数错误")
    void register_missingPassword_fails() throws Exception {
        String invalidJson = """
                {
                    "userId": "TEST_MISSING_PWD",
                    "nickname": "测试用户",
                    "contactPhone": "13800138007",
                    "role": "STUDENT"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    // ==================== 登录测试 ====================

    @Test
    @DisplayName("正确密码登录成功")
    void login_success() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "登录测试用户",
                "13800138009",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest(userId, "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userId").value(userId))
                .andExpect(jsonPath("$.user.nickname").value("登录测试用户"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("password").doesNotContain("Password");
    }

    @Test
    @DisplayName("登录成功返回有效的JWT Token")
    void login_returnsValidToken() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "Token测试用户",
                "13800138010",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest(userId, "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).path("token").asText();

        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("错误密码登录失败")
    void login_wrongPassword_fails() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "密码测试用户",
                "13800138011",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest(userId, "wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("不存在账号登录失败")
    void login_nonExistentUser_fails() throws Exception {
        LoginRequest loginRequest = new LoginRequest("nonexistent_user_12345", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("禁用账号登录失败")
    void login_deactivatedUser_fails() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "禁用测试用户",
                "13800138012",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        // 禁用用户 - 通过Repository直接操作
        var user = userService.loadActiveUser(userId);
        user.setIsActive(false);

        LoginRequest loginRequest = new LoginRequest(userId, "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    // ==================== 路径保护测试 ====================

    @Test
    @DisplayName("注册接口允许匿名访问")
    void register_allowedWithoutAuth() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest request = new UserRegisterRequest(
                userId,
                "password123",
                "匿名注册测试",
                "13800138013",
                UserRole.STUDENT
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("登录接口允许匿名访问")
    void login_allowedWithoutAuth() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "匿名登录测试",
                "13800138014",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest(userId, "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    // ==================== 响应结构兼容性测试 ====================

    @Test
    @DisplayName("错误响应包含message字段")
    void errorResponse_containsMessage() throws Exception {
        LoginRequest loginRequest = new LoginRequest("nonexistent_user_xyz", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("成功响应结构与当前前端兼容")
    void successResponse_structure() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "响应结构测试",
                "13800138015",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest(userId, "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").exists())
                .andExpect(jsonPath("$.user.userId").exists())
                .andExpect(jsonPath("$.user.nickname").exists())
                .andExpect(jsonPath("$.user.role").exists())
                .andExpect(jsonPath("$.user.active").exists())
                .andExpect(jsonPath("$.token").exists());
    }
}