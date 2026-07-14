package com.qiyun.userservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户服务上下文启动测试
 */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceApplicationTests {

    @Test
    @DisplayName("Spring上下文成功启动")
    void contextLoads() {
        // 如果上下文无法启动，测试会自动失败
        assertThat(true).isTrue();
    }
}