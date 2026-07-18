package com.qiyun.userservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * 管理员用户管理接口测试
 * 测试/api/admin/users/**路径的所有操作
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class AdminUserApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    private static int userCounter = 0;

    private String generateUniqueUserId() {
        return String.format("ADM%d%04d", System.currentTimeMillis() % 10000, ++userCounter);
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

    // ==================== 权限测试 ====================

    @Nested
    @DisplayName("权限测试")
    class PermissionTests {

        @Test
        @DisplayName("未登录访问/api/admin/users返回401")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("STUDENT访问/api/admin/users返回403")
        void studentAccess_returns403() throws Exception {
            String token = registerAndLogin(generateUniqueUserId(), "password123", "学生", UserRole.STUDENT);

            mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("STAFF访问/api/admin/users返回403")
        void staffAccess_returns403() throws Exception {
            String token = registerAndLogin(generateUniqueUserId(), "password123", "维修工", UserRole.STAFF);

            mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN访问/api/admin/users成功")
        void adminAccess_success() throws Exception {
            String token = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);

            mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    // ==================== 列表和分页测试 ====================

    @Nested
    @DisplayName("列表和分页测试")
    class ListAndPagingTests {

        @Test
        @DisplayName("管理员获取用户分页列表成功")
        void listUsers_success() throws Exception {
            String adminToken = registerAndLogin("admin001", "password123", "管理员", UserRole.ADMIN);

            mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.list").isArray())
                    .andExpect(jsonPath("$.total").isNumber());
        }

        @Test
        @DisplayName("分页响应字段与前端兼容")
        void pagedResult_fieldCompatible() throws Exception {
            String adminToken = registerAndLogin("admin002", "password123", "管理员", UserRole.ADMIN);

            MvcResult result = mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).contains("\"list\"");
            assertThat(responseBody).contains("\"total\"");
        }

        @Test
        @DisplayName("列表不包含password字段")
        void listUsers_noPasswordField() throws Exception {
            String adminToken = registerAndLogin("admin003", "password123", "管理员", UserRole.ADMIN);

            MvcResult result = mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).doesNotContain("password");
        }

        @Test
        @DisplayName("列表不包含passwordHash字段")
        void listUsers_noPasswordHashField() throws Exception {
            String adminToken = registerAndLogin("admin004", "password123", "管理员", UserRole.ADMIN);

            MvcResult result = mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).doesNotContain("passwordHash");
            assertThat(responseBody).doesNotContain("password_hash");
        }
    }

    // ==================== 创建用户测试 ====================

    @Nested
    @DisplayName("创建用户测试")
    class CreateUserTests {

        @Test
        @DisplayName("管理员创建STUDENT成功")
        void createStudent_success() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);

            UserRegisterRequest request = new UserRegisterRequest(
                    generateUniqueUserId(), "password123", "新学生", "13900139001", UserRole.STUDENT);

            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nickname").value("新学生"))
                    .andExpect(jsonPath("$.role").value("STUDENT"));
        }

        @Test
        @DisplayName("管理员创建STAFF成功")
        void createStaff_success() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);

            UserRegisterRequest request = new UserRegisterRequest(
                    generateUniqueUserId(), "password123", "新维修工", "13900139002", UserRole.STAFF);

            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("STAFF"));
        }

        @Test
        @DisplayName("管理员创建ADMIN成功")
        void createAdmin_success() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);

            UserRegisterRequest request = new UserRegisterRequest(
                    generateUniqueUserId(), "password123", "新管理员", "13900139003", UserRole.ADMIN);

            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @DisplayName("创建用户响应不包含密码")
        void createUser_responseNoPassword() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);

            UserRegisterRequest request = new UserRegisterRequest(
                    generateUniqueUserId(), "password123", "用户", "13900139004", UserRole.STUDENT);

            MvcResult result = mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).doesNotContain("password123");
            assertThat(responseBody).doesNotContain("passwordHash");
        }

        @Test
        @DisplayName("重复userId创建失败")
        void createUser_duplicateUserId_fails() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);

            String duplicateUserId = generateUniqueUserId();
            UserRegisterRequest request1 = new UserRegisterRequest(
                    duplicateUserId, "password123", "用户1", "13900139005", UserRole.STUDENT);

            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request1)))
                    .andExpect(status().isCreated());

            // 使用相同userId再次创建
            UserRegisterRequest request2 = new UserRegisterRequest(
                    duplicateUserId, "password456", "用户2", "13900139006", UserRole.STUDENT);

            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("普通用户不能创建用户")
        void studentCannotCreateUser() throws Exception {
            String studentToken = registerAndLogin(generateUniqueUserId(), "password123", "学生", UserRole.STUDENT);

            UserRegisterRequest request = new UserRegisterRequest(
                    generateUniqueUserId(), "password123", "新用户", "13900139007", UserRole.STUDENT);

            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== 更新用户测试 ====================

    @Nested
    @DisplayName("更新用户测试")
    class UpdateUserTests {

        @Test
        @DisplayName("管理员更新nickname成功")
        void updateNickname_success() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();

            // 先创建用户
            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "password123", "原昵称", "13900139008", UserRole.STUDENT);
            userService.register(createRequest);

            // 更新昵称
            UserRegisterRequest updateRequest = new UserRegisterRequest(
                    targetUserId, "password123", "新昵称", "13900139009", UserRole.STUDENT);

            mockMvc.perform(put("/api/admin/users/" + targetUserId)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").value("新昵称"));
        }

        @Test
        @DisplayName("管理员更新contactPhone成功")
        void updateContactPhone_success() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "password123", "用户", "13900139010", UserRole.STUDENT);
            userService.register(createRequest);

            UserRegisterRequest updateRequest = new UserRegisterRequest(
                    targetUserId, "password123", "用户", "18912345678", UserRole.STUDENT);

            mockMvc.perform(put("/api/admin/users/" + targetUserId)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contactPhone").value("18912345678"));
        }

        @Test
        @DisplayName("更新响应不包含密码")
        void updateUser_responseNoPassword() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "password123", "用户", "13900139011", UserRole.STUDENT);
            userService.register(createRequest);

            UserRegisterRequest updateRequest = new UserRegisterRequest(
                    targetUserId, "password123", "更新用户", "13900139012", UserRole.STUDENT);

            MvcResult result = mockMvc.perform(put("/api/admin/users/" + targetUserId)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).doesNotContain("password");
        }

        @Test
        @DisplayName("更新不存在用户失败")
        void updateNonExistentUser_fails() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);

            UserRegisterRequest updateRequest = new UserRegisterRequest(
                    "nonexistent123", "password123", "用户", "13900139013", UserRole.STUDENT);

            mockMvc.perform(put("/api/admin/users/nonexistent123")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== 禁用和启用测试 ====================

    @Nested
    @DisplayName("禁用和启用测试")
    class DeactivateActivateTests {

        @Test
        @DisplayName("管理员禁用用户成功")
        void deactivateUser_success() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "password123", "待禁用用户", "13900139014", UserRole.STUDENT);
            userService.register(createRequest);

            mockMvc.perform(delete("/api/admin/users/" + targetUserId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("禁用操作不是物理删除")
        void deactivateIsNotPhysicalDelete() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "password123", "用户", "13900139015", UserRole.STUDENT);
            userService.register(createRequest);

            mockMvc.perform(delete("/api/admin/users/" + targetUserId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());

            // 验证用户仍然存在，只是被禁用
            var user = userService.findById(targetUserId);
            assertThat(user.active()).isFalse();
        }

        @Test
        @DisplayName("禁用用户不能登录")
        void deactivatedUserCannotLogin() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();
            String targetPassword = "password123";

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, targetPassword, "用户", "13900139016", UserRole.STUDENT);
            userService.register(createRequest);

            // 禁用用户
            mockMvc.perform(delete("/api/admin/users/" + targetUserId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());

            // 验证用户无法登录
            LoginRequest loginRequest = new LoginRequest(targetUserId, targetPassword);
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("管理员重新启用用户成功")
        void reactivateUser_success() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "password123", "用户", "13900139017", UserRole.STUDENT);
            userService.register(createRequest);

            // 禁用
            userService.deactivate(targetUserId);

            // 重新启用（使用PATCH方法）
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/users/" + targetUserId + "/activate")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());

            // 验证用户已启用
            var user = userService.findById(targetUserId);
            assertThat(user.active()).isTrue();
        }

        @Test
        @DisplayName("重新启用后用户可以登录")
        void reactivatedUserCanLogin() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();
            String targetPassword = "password123";

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, targetPassword, "用户", "13900139018", UserRole.STUDENT);
            userService.register(createRequest);

            // 禁用
            userService.deactivate(targetUserId);

            // 重新启用
            userService.activate(targetUserId);

            // 验证可以登录
            LoginRequest loginRequest = new LoginRequest(targetUserId, targetPassword);
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("普通用户不能禁用账号")
        void studentCannotDeactivate() throws Exception {
            String studentToken = registerAndLogin(generateUniqueUserId(), "password123", "学生", UserRole.STUDENT);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "password123", "目标用户", "13900139019", UserRole.STUDENT);
            userService.register(createRequest);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/users/" + targetUserId + "/deactivate")
                            .header("Authorization", "Bearer " + studentToken))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== 重置密码测试 ====================

    @Nested
    @DisplayName("重置密码测试")
    class ResetPasswordTests {

        @Test
        @DisplayName("管理员重置密码成功")
        void resetPassword_success() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "oldPassword", "用户", "13900139020", UserRole.STUDENT);
            userService.register(createRequest);

            mockMvc.perform(post("/api/admin/users/" + targetUserId + "/reset-password")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("密码重置成功"))
                    .andExpect(jsonPath("$.userId").value(targetUserId))
                    .andExpect(jsonPath("$.newPassword").exists());
        }

        @Test
        @DisplayName("返回临时密码但不返回密码哈希")
        void resetPassword_returnsTempPasswordNotHash() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "oldPassword", "用户", "13900139021", UserRole.STUDENT);
            userService.register(createRequest);

            MvcResult result = mockMvc.perform(post("/api/admin/users/" + targetUserId + "/reset-password")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).contains("newPassword");
            assertThat(responseBody).doesNotContain("passwordHash");
            assertThat(responseBody).doesNotContain("password_hash");
        }

        @Test
        @DisplayName("临时密码不是固定默认值")
        void tempPasswordNotFixed() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId1 = generateUniqueUserId();
            String targetUserId2 = generateUniqueUserId();

            UserRegisterRequest createRequest1 = new UserRegisterRequest(
                    targetUserId1, "password1", "用户1", "13900139022", UserRole.STUDENT);
            userService.register(createRequest1);

            UserRegisterRequest createRequest2 = new UserRegisterRequest(
                    targetUserId2, "password2", "用户2", "13900139023", UserRole.STUDENT);
            userService.register(createRequest2);

            MvcResult result1 = mockMvc.perform(post("/api/admin/users/" + targetUserId1 + "/reset-password")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            MvcResult result2 = mockMvc.perform(post("/api/admin/users/" + targetUserId2 + "/reset-password")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String password1 = objectMapper.readTree(result1.getResponse().getContentAsString())
                    .path("newPassword").asText();
            String password2 = objectMapper.readTree(result2.getResponse().getContentAsString())
                    .path("newPassword").asText();

            // 两次重置的临时密码应该不同
            assertThat(password1).isNotEqualTo(password2);
        }

        @Test
        @DisplayName("连续重置两次产生不同临时密码")
        void consecutiveResetsDifferentPasswords() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "password", "用户", "13900139024", UserRole.STUDENT);
            userService.register(createRequest);

            MvcResult result1 = mockMvc.perform(post("/api/admin/users/" + targetUserId + "/reset-password")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String password1 = objectMapper.readTree(result1.getResponse().getContentAsString())
                    .path("newPassword").asText();

            MvcResult result2 = mockMvc.perform(post("/api/admin/users/" + targetUserId + "/reset-password")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String password2 = objectMapper.readTree(result2.getResponse().getContentAsString())
                    .path("newPassword").asText();

            assertThat(password1).isNotEqualTo(password2);
        }

        @Test
        @DisplayName("旧密码不能登录")
        void oldPasswordCannotLogin() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();
            String oldPassword = "oldPassword123";

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, oldPassword, "用户", "13900139025", UserRole.STUDENT);
            userService.register(createRequest);

            // 重置密码
            mockMvc.perform(post("/api/admin/users/" + targetUserId + "/reset-password")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            // 用旧密码登录应该失败
            LoginRequest loginRequest = new LoginRequest(targetUserId, oldPassword);
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("临时密码可以登录")
        void tempPasswordCanLogin() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "originalPassword", "用户", "13900139026", UserRole.STUDENT);
            userService.register(createRequest);

            MvcResult result = mockMvc.perform(post("/api/admin/users/" + targetUserId + "/reset-password")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String tempPassword = objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("newPassword").asText();

            LoginRequest loginRequest = new LoginRequest(targetUserId, tempPassword);
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("普通用户不能重置他人密码")
        void studentCannotResetPassword() throws Exception {
            String studentToken = registerAndLogin(generateUniqueUserId(), "password123", "学生", UserRole.STUDENT);
            String targetUserId = generateUniqueUserId();

            UserRegisterRequest createRequest = new UserRegisterRequest(
                    targetUserId, "password", "目标用户", "13900139027", UserRole.STUDENT);
            userService.register(createRequest);

            mockMvc.perform(post("/api/admin/users/" + targetUserId + "/reset-password")
                            .header("Authorization", "Bearer " + studentToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("不存在用户重置失败")
        void resetPassword_nonExistentUser_fails() throws Exception {
            String adminToken = registerAndLogin(generateUniqueUserId(), "password123", "管理员", UserRole.ADMIN);

            mockMvc.perform(post("/api/admin/users/nonexistent999/reset-password")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
    }
}
