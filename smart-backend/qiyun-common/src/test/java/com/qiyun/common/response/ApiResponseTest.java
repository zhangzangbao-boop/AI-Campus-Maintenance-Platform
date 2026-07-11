package com.qiyun.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qiyun.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ApiResponse 单元测试
 */
class ApiResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void testSuccess_noData() {
        ApiResponse<Void> response = ApiResponse.success();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("操作成功");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void testSuccess_withData() {
        String testData = "测试数据";
        ApiResponse<String> response = ApiResponse.success(testData);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("操作成功");
        assertThat(response.getData()).isEqualTo("测试数据");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void testSuccess_customMessage() {
        Integer testData = 42;
        ApiResponse<Integer> response = ApiResponse.success("自定义成功消息", testData);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("自定义成功消息");
        assertThat(response.getData()).isEqualTo(42);
    }

    @Test
    void testFail_errorCode() {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.NOT_FOUND);

        assertThat(response.getCode()).isEqualTo(404);
        assertThat(response.getMessage()).isEqualTo("资源不存在");
        assertThat(response.getData()).isNull();
    }

    @Test
    void testFail_errorCodeWithMessage() {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.VALIDATION_ERROR, "用户名不能为空");

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("用户名不能为空");
        assertThat(response.getData()).isNull();
    }

    @Test
    void testFail_customCodeAndMessage() {
        ApiResponse<Void> response = ApiResponse.fail(418, "我是一个茶壶");

        assertThat(response.getCode()).isEqualTo(418);
        assertThat(response.getMessage()).isEqualTo("我是一个茶壶");
    }

    @Test
    void testTimestamp_notNull() {
        ApiResponse<String> response = ApiResponse.success("test");

        assertThat(response.getTimestamp()).isNotNull();
        // 验证时间戳在当前时间附近（允许1秒误差）
        long now = System.currentTimeMillis();
        long responseTime = response.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertThat(Math.abs(now - responseTime)).isLessThan(2000);
    }

    @Test
    void testJacksonSerialization_success() throws Exception {
        ApiResponse<String> response = ApiResponse.success("测试数据");
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"code\":200");
        assertThat(json).contains("\"message\":\"操作成功\"");
        assertThat(json).contains("\"data\":\"测试数据\"");
        assertThat(json).contains("\"timestamp\"");
        // 验证不包含异常堆栈
        assertThat(json).doesNotContain("stackTrace");
        assertThat(json).doesNotContain("exception");
    }

    @Test
    void testJacksonSerialization_fail() throws Exception {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.NOT_FOUND);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"code\":404");
        assertThat(json).contains("\"message\":\"资源不存在\"");
        assertThat(json).contains("\"timestamp\"");
        // 验证 data 字段不存在（因为为 null 且使用 NON_NULL）
        assertThat(json).doesNotContain("\"data\"");
    }

    @Test
    void testSerialization_noStackTrace() throws Exception {
        // 测试序列化结果不包含异常堆栈信息
        ApiResponse<String> response = ApiResponse.success("测试数据");
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).doesNotContain("stackTrace");
        assertThat(json).doesNotContain("exception");
        assertThat(json).doesNotContain("cause");
    }
}