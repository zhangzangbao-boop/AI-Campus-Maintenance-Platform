package com.qiyun.userservice;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.userservice.domain.enums.UserRole;
import com.qiyun.userservice.dto.UserDto;
import com.qiyun.userservice.dto.request.LoginRequest;
import com.qiyun.userservice.dto.request.UserRegisterRequest;
import com.qiyun.userservice.dto.response.AuthResponse;
import com.qiyun.userservice.repository.UserRepository;
import com.qiyun.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class UserServiceTests {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static int userCounter = 0;

    private String generateUniqueUserId() {
        return String.format("TEST%d%04d", System.currentTimeMillis() % 10000, ++userCounter);
    }

    @BeforeEach
    void setUp() {
        userCounter++;
    }

    @Test
    @DisplayName("注册成功创建用户")
    void register_success() {
        String userId = generateUniqueUserId();
        UserRegisterRequest request = new UserRegisterRequest(
                userId,
                "password123",
                "测试用户",
                "13800138001",
                UserRole.STUDENT
        );

        UserDto result = userService.register(request);

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.nickname()).isEqualTo("测试用户");
        assertThat(result.role()).isEqualTo(UserRole.STUDENT);
        assertThat(result.active()).isTrue();
    }

    @Test
    @DisplayName("注册密码使用BCrypt加密")
    void register_passwordEncoded() {
        String userId = generateUniqueUserId();
        UserRegisterRequest request = new UserRegisterRequest(
                userId,
                "password123",
                "测试用户",
                "13800138002",
                UserRole.STUDENT
        );

        userService.register(request);

        var user = userRepository.findByUserId(userId).orElseThrow();
        assertThat(passwordEncoder.matches("password123", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo("password123");
    }

    @Test
    @DisplayName("重复用户ID注册失败")
    void register_duplicateUserId_fails() {
        String userId = generateUniqueUserId();
        UserRegisterRequest request1 = new UserRegisterRequest(
                userId,
                "password123",
                "第一个用户",
                "13800138003",
                UserRole.STUDENT
        );

        userService.register(request1);

        UserRegisterRequest request2 = new UserRegisterRequest(
                userId,
                "password456",
                "第二个用户",
                "13800138004",
                UserRole.STUDENT
        );

        assertThatThrownBy(() -> userService.register(request2))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("登录成功返回用户信息")
    void login_success() {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "登录测试用户",
                "13800138005",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest(userId, "password123");
        AuthResponse result = userService.login(loginRequest);

        assertThat(result).isNotNull();
        assertThat(result.user().userId()).isEqualTo(userId);
        assertThat(result.user().nickname()).isEqualTo("登录测试用户");
    }

    @Test
    @DisplayName("错误密码登录失败")
    void login_wrongPassword_fails() {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "密码测试用户",
                "13800138006",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest(userId, "wrongpassword");

        assertThatThrownBy(() -> userService.login(loginRequest))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("不存在用户登录失败")
    void login_nonExistentUser_fails() {
        LoginRequest loginRequest = new LoginRequest("nonexistent_user", "password123");

        assertThatThrownBy(() -> userService.login(loginRequest))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("根据用户ID查询用户")
    void findById_success() {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "查询测试用户",
                "13800138007",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        UserDto result = userService.findById(userId);

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("查询不存在用户抛出异常")
    void findById_nonExistentUser_fails() {
        assertThatThrownBy(() -> userService.findById("nonexistent_user"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("loadUserByUsername返回正确UserDetails")
    void loadUserByUsername_success() {
        String userId = generateUniqueUserId();
        UserRegisterRequest registerRequest = new UserRegisterRequest(
                userId,
                "password123",
                "UserDetails测试用户",
                "13800138008",
                UserRole.STUDENT
        );
        userService.register(registerRequest);

        var userDetails = userService.loadUserByUsername(userId);

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(userId);
        assertThat(userDetails.getAuthorities()).extracting("authority")
                .contains("ROLE_STUDENT");
        assertThat(userDetails.isEnabled()).isTrue();
    }
}