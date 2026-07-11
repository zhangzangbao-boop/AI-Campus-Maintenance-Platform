package com.ligong.reportingcenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.request.LoginRequest;
import com.ligong.reportingcenter.dto.request.UserRegisterRequest;
import com.ligong.reportingcenter.dto.response.AuthResponse;
import com.ligong.reportingcenter.exception.BusinessException;
import com.ligong.reportingcenter.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class UserServiceTests {

    @Autowired
    private UserService userService;

    @Test
    void registerAndLoginSuccess() {
        UserRegisterRequest registerRequest = new UserRegisterRequest(
            "20250001",
            "password123",
            "张三",
            "13800138000",
            UserRole.STUDENT
        );
        userService.register(registerRequest);

        AuthResponse response = userService.login(new LoginRequest("20250001", "password123"));
        assertThat(response.user().userId()).isEqualTo("20250001");
        assertThat(response.user().role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void testRegisterDuplicateUser() {
        UserRegisterRequest registerRequest = new UserRegisterRequest(
            "20250002",
            "password123",
            "李四",
            "13800138001",
            UserRole.STUDENT
        );
        userService.register(registerRequest);

        assertThrows(BusinessException.class, () -> {
            userService.register(registerRequest);
        });
    }

    @Test
    void testLoginWithWrongPassword() {
        UserRegisterRequest registerRequest = new UserRegisterRequest(
            "20250003",
            "password123",
            "王五",
            "13800138002",
            UserRole.STUDENT
        );
        userService.register(registerRequest);

        assertThrows(BusinessException.class, () -> {
            userService.login(new LoginRequest("20250003", "wrongpassword"));
        });
    }

    @Test
    void testLoginNonExistentUser() {
        assertThrows(BusinessException.class, () -> {
            userService.login(new LoginRequest("nonexistent", "password"));
        });
    }

    @Test
    void testFindById() {
        UserRegisterRequest registerRequest = new UserRegisterRequest(
            "20250004",
            "password123",
            "赵六",
            "13800138003",
            UserRole.STUDENT
        );
        userService.register(registerRequest);

        var userDto = userService.findById("20250004");
        assertThat(userDto.userId()).isEqualTo("20250004");
        assertThat(userDto.nickname()).isEqualTo("赵六");
    }

    @Test
    void testFindNonExistentUser() {
        assertThrows(BusinessException.class, () -> {
            userService.findById("nonexistent");
        });
    }

    @Test
    void testListByRole() {
        UserRegisterRequest registerRequest1 = new UserRegisterRequest(
            "20250005",
            "password123",
            "学生甲",
            "13800138004",
            UserRole.STUDENT
        );
        UserRegisterRequest registerRequest2 = new UserRegisterRequest(
            "20250006",
            "password123",
            "学生乙",
            "13800138005",
            UserRole.STUDENT
        );
        userService.register(registerRequest1);
        userService.register(registerRequest2);

        List<?> students = userService.listByRole(UserRole.STUDENT);
        assertThat(students).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void testDeactivateUser() {
        UserRegisterRequest registerRequest = new UserRegisterRequest(
            "20250007",
            "password123",
            "测试用户",
            "13800138006",
            UserRole.STUDENT
        );
        userService.register(registerRequest);

        userService.deactivate("20250007");

        assertThrows(BusinessException.class, () -> {
            userService.login(new LoginRequest("20250007", "password123"));
        });
    }
}