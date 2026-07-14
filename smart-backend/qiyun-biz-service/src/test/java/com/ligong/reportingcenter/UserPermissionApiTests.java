package com.ligong.reportingcenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.request.ChangePasswordRequest;
import com.ligong.reportingcenter.dto.request.LoginRequest;
import com.ligong.reportingcenter.dto.request.UserRegisterRequest;
import com.ligong.reportingcenter.dto.request.UserUpdateRequest;
import com.ligong.reportingcenter.service.UserService;
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

/**
 * 用户权限接口测试
 * 使用MockMvc测试个人信息、密码修改和管理员权限
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class UserPermissionApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    private static int userCounter = 0;

    private String generateUniqueUserId() {
        return String.format("PERM%d%04d", System.currentTimeMillis() % 10000, ++userCounter);
    }

    private String registerAndLogin(String userId, String password, String nickname, UserRole role) throws Exception {
        // 注册
        UserRegisterRequest registerRequest = new UserRegisterRequest(userId, password, nickname, "13800138000", role);
        userService.register(registerRequest);

        // 登录获取Token
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

    // ==================== 个人信息接口测试 ====================

    @Test
    @DisplayName("未携带Token访问/api/users/me被拒绝")
    void getCurrentUser_withoutToken_fails() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("合法学生Token可以获取本人信息")
    void getCurrentUser_withStudentToken_success() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "学生用户", UserRole.STUDENT);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.nickname").value("学生用户"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.active").value(true))
                // 确保不返回密码相关字段
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("合法维修工Token可以获取本人信息")
    void getCurrentUser_withStaffToken_success() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "维修工用户", UserRole.STAFF);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.role").value("STAFF"));
    }

    @Test
    @DisplayName("合法管理员Token可以获取本人信息")
    void getCurrentUser_withAdminToken_success() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "管理员用户", UserRole.ADMIN);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("返回内容不包含密码或密码哈希")
    void getCurrentUser_noPasswordInResponse() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "安全测试用户", UserRole.STUDENT);

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("password").doesNotContain("Password").doesNotContain("hash");
    }

    // ==================== 修改个人信息测试 ====================

    @Test
    @DisplayName("用户可以修改自己的昵称和联系方式")
    void updateUserInfo_success() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "原昵称", UserRole.STUDENT);

        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setNickname("新昵称");
        updateRequest.setContactPhone("13900139000");
        updateRequest.setAvatarUrl("http://example.com/avatar.png");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("新昵称"))
                .andExpect(jsonPath("$.contactPhone").value("13900139000"));
    }

    @Test
    @DisplayName("用户不能通过个人信息接口修改自己的角色")
    void updateUserInfo_cannotChangeRole() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "角色测试用户", UserRole.STUDENT);

        // 尝试修改昵称（正常操作）
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setNickname("修改后的昵称");
        updateRequest.setContactPhone("13900139001");
        updateRequest.setAvatarUrl("http://example.com/avatar2.png");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        // 验证角色未改变
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    @DisplayName("用户不能通过个人信息接口修改自己的启用状态")
    void updateUserInfo_cannotChangeActive() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "状态测试用户", UserRole.STUDENT);

        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setNickname("新昵称");
        updateRequest.setContactPhone("13900139002");
        updateRequest.setAvatarUrl("http://example.com/avatar3.png");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        // 验证active状态未改变
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    // ==================== 密码修改测试 ====================

    @Test
    @DisplayName("使用正确原密码修改密码成功")
    void changePassword_success() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "密码测试用户", UserRole.STUDENT);

        ChangePasswordRequest changeRequest = new ChangePasswordRequest("password123", "newPassword456");

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("原密码错误时修改失败")
    void changePassword_wrongOldPassword_fails() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "密码错误测试", UserRole.STUDENT);

        ChangePasswordRequest changeRequest = new ChangePasswordRequest("wrongOldPassword", "newPassword456");

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("修改密码后旧密码不能登录")
    void changePassword_oldPasswordCannotLogin() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "密码验证测试", UserRole.STUDENT);

        // 修改密码
        ChangePasswordRequest changeRequest = new ChangePasswordRequest("password123", "newPassword456");
        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isOk());

        // 使用旧密码登录失败
        LoginRequest loginRequest = new LoginRequest(userId, "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("修改密码后新密码可以登录")
    void changePassword_newPasswordCanLogin() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "新密码测试", UserRole.STUDENT);

        // 修改密码
        ChangePasswordRequest changeRequest = new ChangePasswordRequest("password123", "newPassword456");
        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isOk());

        // 使用新密码登录成功
        LoginRequest loginRequest = new LoginRequest(userId, "newPassword456");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userId").value(userId));
    }

    // ==================== 用户查询权限测试 ====================

    @Test
    @DisplayName("普通学生不能通过用户ID接口查看其他用户的敏感信息")
    void getUserById_studentCannotAccessOtherUser() throws Exception {
        // 创建两个用户
        String studentId = generateUniqueUserId();
        String adminId = generateUniqueUserId();

        registerAndLogin(studentId, "password123", "学生用户", UserRole.STUDENT);
        registerAndLogin(adminId, "password123", "管理员用户", UserRole.ADMIN);

        // 学生Token尝试获取管理员信息
        LoginRequest loginRequest = new LoginRequest(studentId, "password123");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        String studentToken = objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();

        mockMvc.perform(get("/api/users/" + adminId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // ==================== 管理员权限测试 ====================

    @Test
    @DisplayName("未登录用户不能访问/api/admin/users")
    void adminUsers_withoutToken_fails() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("学生不能访问/api/admin/users")
    void adminUsers_studentDenied() throws Exception {
        String studentId = generateUniqueUserId();
        String token = registerAndLogin(studentId, "password123", "学生用户", UserRole.STUDENT);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("维修工不能访问/api/admin/users")
    void adminUsers_staffDenied() throws Exception {
        String staffId = generateUniqueUserId();
        String token = registerAndLogin(staffId, "password123", "维修工用户", UserRole.STAFF);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员可以查看用户列表")
    void adminUsers_adminCanAccess() throws Exception {
        String adminId = generateUniqueUserId();
        String token = registerAndLogin(adminId, "password123", "管理员用户", UserRole.ADMIN);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list").exists())
                .andExpect(jsonPath("$.total").exists());
    }

    @Test
    @DisplayName("管理员可以创建维修工账号")
    void adminCreateUser_staffSuccess() throws Exception {
        String adminId = generateUniqueUserId();
        String token = registerAndLogin(adminId, "password123", "管理员用户", UserRole.ADMIN);

        String newStaffId = generateUniqueUserId();
        UserRegisterRequest createRequest = new UserRegisterRequest(
                newStaffId,
                "password123",
                "新建维修工",
                "13800138100",
                UserRole.STAFF
        );

        MvcResult result = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(newStaffId))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andReturn();

        // 确保不返回密码
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("password").doesNotContain("Password");
    }

    @Test
    @DisplayName("创建结果不包含密码或密码哈希")
    void adminCreateUser_noPasswordInResponse() throws Exception {
        String adminId = generateUniqueUserId();
        String token = registerAndLogin(adminId, "password123", "管理员用户", UserRole.ADMIN);

        String newUserId = generateUniqueUserId();
        UserRegisterRequest createRequest = new UserRegisterRequest(
                newUserId,
                "password123",
                "新建用户",
                "13800138101",
                UserRole.STUDENT
        );

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("管理员可以禁用用户")
    void adminDeactivateUser_success() throws Exception {
        String adminId = generateUniqueUserId();
        String token = registerAndLogin(adminId, "password123", "管理员用户", UserRole.ADMIN);

        // 创建要禁用的用户
        String targetUserId = generateUniqueUserId();
        registerAndLogin(targetUserId, "password123", "待禁用用户", UserRole.STUDENT);

        // AdminController使用DELETE方法
        mockMvc.perform(delete("/api/admin/users/" + targetUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 验证用户被禁用后无法登录
        LoginRequest loginRequest = new LoginRequest(targetUserId, "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("被禁用用户无法登录")
    void deactivatedUser_cannotLogin() throws Exception {
        String userId = generateUniqueUserId();
        registerAndLogin(userId, "password123", "测试用户", UserRole.STUDENT);

        // 禁用用户
        userService.deactivate(userId);

        // 尝试登录
        LoginRequest loginRequest = new LoginRequest(userId, "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("管理员可以重置密码")
    void adminResetPassword_success() throws Exception {
        String adminId = generateUniqueUserId();
        String token = registerAndLogin(adminId, "password123", "管理员用户", UserRole.ADMIN);

        // 创建目标用户
        String targetUserId = generateUniqueUserId();
        registerAndLogin(targetUserId, "password123", "重置密码用户", UserRole.STUDENT);

        mockMvc.perform(post("/api/admin/users/" + targetUserId + "/reset-password")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.newPassword").exists());
    }

    @Test
    @DisplayName("重置后旧密码不能登录")
    void resetPassword_oldPasswordCannotLogin() throws Exception {
        String adminId = generateUniqueUserId();
        String token = registerAndLogin(adminId, "password123", "管理员用户", UserRole.ADMIN);

        String targetUserId = generateUniqueUserId();
        registerAndLogin(targetUserId, "password123", "重置密码测试", UserRole.STUDENT);

        // 重置密码
        mockMvc.perform(post("/api/admin/users/" + targetUserId + "/reset-password")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 使用旧密码登录失败
        LoginRequest loginRequest = new LoginRequest(targetUserId, "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("普通用户不能调用重置密码接口")
    void resetPassword_studentDenied() throws Exception {
        String studentId = generateUniqueUserId();
        String token = registerAndLogin(studentId, "password123", "学生用户", UserRole.STUDENT);

        String targetUserId = generateUniqueUserId();
        registerAndLogin(targetUserId, "password123", "目标用户", UserRole.STUDENT);

        mockMvc.perform(post("/api/admin/users/" + targetUserId + "/reset-password")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("普通用户不能调用禁用用户接口")
    void deactivateUser_studentDenied() throws Exception {
        String studentId = generateUniqueUserId();
        String token = registerAndLogin(studentId, "password123", "学生用户", UserRole.STUDENT);

        String targetUserId = generateUniqueUserId();
        registerAndLogin(targetUserId, "password123", "目标用户", UserRole.STUDENT);

        mockMvc.perform(delete("/api/admin/users/" + targetUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ==================== 禁用用户Token测试 ====================

    @Test
    @DisplayName("禁用用户前的Token可以正常访问")
    void enabledUser_tokenWorks() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "测试用户", UserRole.STUDENT);

        // 验证Token可以访问
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    @DisplayName("禁用用户后旧Token被拒绝")
    void disabledUser_oldTokenRejected() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "待禁用用户", UserRole.STUDENT);

        // 验证Token最初可以访问
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 禁用用户
        userService.deactivate(userId);

        // 使用旧Token访问被拒绝
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("禁用用户不能访问管理员接口")
    void disabledUser_cannotAccessAdminEndpoint() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "待禁用管理员", UserRole.ADMIN);

        // 验证管理员Token最初可以访问
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 禁用管理员
        userService.deactivate(userId);

        // 使用旧Token访问管理员接口被拒绝
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("重新启用用户后未过期Token恢复有效")
    void reenabledUser_tokenRestored() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "待禁用用户", UserRole.STUDENT);

        // 禁用用户
        userService.deactivate(userId);

        // Token被拒绝
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // 重新启用用户
        userService.activate(userId);

        // 未过期Token恢复有效
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    @DisplayName("禁用一个用户不影响其他用户")
    void disableOneUser_doesNotAffectOthers() throws Exception {
        String userId1 = generateUniqueUserId();
        String userId2 = generateUniqueUserId();

        String token1 = registerAndLogin(userId1, "password123", "用户1", UserRole.STUDENT);
        String token2 = registerAndLogin(userId2, "password123", "用户2", UserRole.STUDENT);

        // 禁用用户1
        userService.deactivate(userId1);

        // 用户1的Token被拒绝
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isUnauthorized());

        // 用户2的Token仍然有效
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId2));
    }

    @Test
    @DisplayName("禁用用户仍然不能登录")
    void disabledUser_cannotLogin() throws Exception {
        String userId = generateUniqueUserId();
        registerAndLogin(userId, "password123", "测试用户", UserRole.STUDENT);

        // 禁用用户
        userService.deactivate(userId);

        // 尝试登录失败
        LoginRequest loginRequest = new LoginRequest(userId, "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    // ==================== SecurityConfig路径保护测试 ====================

    @Test
    @DisplayName("/api/auth/register允许匿名")
    void authRegister_allowedAnonymous() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest request = new UserRegisterRequest(
                userId, "password123", "匿名注册", "13800138200", UserRole.STUDENT);

        // 无Token访问注册接口
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("/api/auth/login允许匿名")
    void authLogin_allowedAnonymous() throws Exception {
        String userId = generateUniqueUserId();
        UserRegisterRequest request = new UserRegisterRequest(
                userId, "password123", "匿名登录", "13800138201", UserRole.STUDENT);
        userService.register(request);

        LoginRequest loginRequest = new LoginRequest(userId, "password123");

        // 无Token访问登录接口
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/api/users/me需要认证")
    void usersMe_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/admin/users需要ADMIN角色")
    void adminUsers_requiresAdmin() throws Exception {
        // 学生访问被拒绝
        String studentId = generateUniqueUserId();
        String studentToken = registerAndLogin(studentId, "password123", "学生", UserRole.STUDENT);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        // 维修工访问被拒绝
        String staffId = generateUniqueUserId();
        String staffToken = registerAndLogin(staffId, "password123", "维修工", UserRole.STAFF);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        // 管理员访问成功
        String adminId = generateUniqueUserId();
        String adminToken = registerAndLogin(adminId, "password123", "管理员", UserRole.ADMIN);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/api/categories允许匿名访问")
    void categories_allowedAnonymous() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }
}