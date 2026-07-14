package com.qiyun.userservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.userservice.domain.enums.UserRole;
import com.qiyun.userservice.dto.request.ChangePasswordRequest;
import com.qiyun.userservice.dto.request.LoginRequest;
import com.qiyun.userservice.dto.request.UserRegisterRequest;
import com.qiyun.userservice.dto.request.UserUpdateRequest;
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

/**
 * 个人信息接口测试
 * 测试/api/users/me和/api/users/me/password
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class UserProfileApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    private static int userCounter = 0;

    private String generateUniqueUserId() {
        return String.format("PROF%d%04d", System.currentTimeMillis() % 10000, ++userCounter);
    }

    private String registerAndLogin(String userId, String password, String nickname, UserRole role) throws Exception {
        // 注册
        UserRegisterRequest registerRequest = new UserRegisterRequest(userId, password, nickname, "13800138000", role);
        userService.register(registerRequest);

        // 登录获取Token
        LoginRequest loginRequest = new LoginRequest(userId, password);
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
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

    // ==================== GET /api/users/me 测试 ====================

    @Test
    @DisplayName("未携带Token访问/api/users/me返回401")
    void getCurrentUser_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("STUDENT携带合法Token获取本人信息成功")
    void getCurrentUser_withStudentToken_success() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "学生用户", UserRole.STUDENT);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.nickname").value("学生用户"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("STAFF携带合法Token获取本人信息成功")
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
    @DisplayName("ADMIN携带合法Token获取本人信息成功")
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
    @DisplayName("返回结果不包含password字段")
    void getCurrentUser_noPasswordField() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "安全测试用户", UserRole.STUDENT);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("返回结果不包含passwordHash字段")
    void getCurrentUser_noPasswordHashField() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "安全测试用户", UserRole.STUDENT);

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("passwordHash");
        assertThat(responseBody).doesNotContain("password_hash");
    }

    // ==================== PUT /api/users/me 测试 ====================

    @Test
    @DisplayName("修改nickname成功")
    void updateUserInfo_updateNickname_success() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "原昵称", UserRole.STUDENT);

        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setNickname("新昵称");
        updateRequest.setContactPhone("13900139000");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("新昵称"));
    }

    @Test
    @DisplayName("修改contactPhone成功")
    void updateUserInfo_updateContactPhone_success() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "用户", UserRole.STUDENT);

        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setNickname("用户");
        updateRequest.setContactPhone("18912345678");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactPhone").value("18912345678"));
    }

    @Test
    @DisplayName("修改avatarUrl成功")
    void updateUserInfo_updateAvatarUrl_success() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "用户", UserRole.STUDENT);

        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setNickname("用户");
        updateRequest.setContactPhone("13900139000");
        updateRequest.setAvatarUrl("http://example.com/new-avatar.png");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("avatarUrl为空时可以修改其他资料")
    void updateUserInfo_emptyAvatarUrl_success() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "用户", UserRole.STUDENT);

        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setNickname("新名字");
        updateRequest.setContactPhone("13900139001");
        updateRequest.setAvatarUrl("");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("新名字"));
    }

    @Test
    @DisplayName("请求中不提供avatarUrl时可以修改其他资料")
    void updateUserInfo_nullAvatarUrl_success() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "用户", UserRole.STUDENT);

        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setNickname("更新昵称");
        updateRequest.setContactPhone("13900139002");
        // avatarUrl为null

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("更新昵称"));
    }

    @Test
    @DisplayName("修改资料请求不能修改role")
    void updateUserInfo_cannotChangeRole() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "角色测试", UserRole.STUDENT);

        // UserUpdateRequest不包含role字段，无法修改role
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setNickname("新昵称");
        updateRequest.setContactPhone("13900139003");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        // 验证角色仍然是STUDENT
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    @DisplayName("修改资料请求不能修改active")
    void updateUserInfo_cannotChangeActive() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "状态测试", UserRole.STUDENT);

        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setNickname("新昵称");
        updateRequest.setContactPhone("13900139004");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        // 验证active仍然是true
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("修改资料请求不能修改userId")
    void updateUserInfo_cannotChangeUserId() throws Exception {
        String originalUserId = generateUniqueUserId();
        String token = registerAndLogin(originalUserId, "password123", "用户", UserRole.STUDENT);

        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setNickname("新昵称");
        updateRequest.setContactPhone("13900139005");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        // 验证userId未改变
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(originalUserId));
    }

    @Test
    @DisplayName("禁用用户旧Token不能访问/me")
    void disabledUser_cannotAccessMe() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "待禁用用户", UserRole.STUDENT);

        // 先验证可以访问
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 禁用用户
        userService.deactivate(userId);

        // 再次访问应该失败
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未登录修改个人信息返回401")
    void updateUserInfo_withoutToken_returns401() throws Exception {
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setNickname("新昵称");
        updateRequest.setContactPhone("13900139006");

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnauthorized());
    }

    // ==================== PUT /api/users/me/password 测试 ====================

    @Test
    @DisplayName("未登录修改密码返回401")
    void changePassword_withoutToken_returns401() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("oldPass123", "newPass456");

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("正确原密码修改成功")
    void changePassword_correctOldPassword_success() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "oldPassword123", "用户", UserRole.STUDENT);

        ChangePasswordRequest request = new ChangePasswordRequest("oldPassword123", "newPassword456");

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("密码修改成功，请使用新密码登录"));
    }

    @Test
    @DisplayName("原密码错误时失败")
    void changePassword_wrongOldPassword_fails() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "correctPassword", "用户", UserRole.STUDENT);

        ChangePasswordRequest request = new ChangePasswordRequest("wrongPassword", "newPassword456");

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("缺少原密码时返回参数错误")
    void changePassword_missingOldPassword_fails() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "用户", UserRole.STUDENT);

        String requestJson = "{\"oldPassword\":\"\", \"newPassword\":\"newPass456\"}";

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("缺少新密码时返回参数错误")
    void changePassword_missingNewPassword_fails() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "用户", UserRole.STUDENT);

        String requestJson = "{\"oldPassword\":\"password123\", \"newPassword\":\"\"}";

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("新密码过短时失败")
    void changePassword_newPasswordTooShort_fails() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "用户", UserRole.STUDENT);

        // 新密码只有4位，小于最小长度6
        ChangePasswordRequest request = new ChangePasswordRequest("password123", "short");

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("修改成功后旧密码不能再登录")
    void changePassword_oldPasswordCannotLogin() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "oldPassword123", "用户", UserRole.STUDENT);

        // 修改密码
        ChangePasswordRequest request = new ChangePasswordRequest("oldPassword123", "newPassword456");
        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 用旧密码登录应该失败
        LoginRequest loginWithOldPassword = new LoginRequest(userId, "oldPassword123");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginWithOldPassword)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("修改成功后新密码可以登录")
    void changePassword_newPasswordCanLogin() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "oldPassword123", "用户", UserRole.STUDENT);

        // 修改密码
        ChangePasswordRequest request = new ChangePasswordRequest("oldPassword123", "newPassword456");
        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 用新密码登录应该成功
        LoginRequest loginWithNewPassword = new LoginRequest(userId, "newPassword456");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginWithNewPassword)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("修改密码响应不包含密码")
    void changePassword_responseDoesNotContainPassword() throws Exception {
        String userId = generateUniqueUserId();
        String token = registerAndLogin(userId, "password123", "用户", UserRole.STUDENT);

        ChangePasswordRequest request = new ChangePasswordRequest("password123", "newPassword456");

        MvcResult result = mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("newPassword456");
        assertThat(responseBody).doesNotContain("password123");
    }
}