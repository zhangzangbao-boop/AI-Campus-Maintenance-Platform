package com.qiyun.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * ErrorCode 单元测试
 */
class ErrorCodeTest {

    @Test
    void testSuccess() {
        assertThat(ErrorCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(ErrorCode.SUCCESS.getDefaultMessage()).isEqualTo("操作成功");
    }

    @Test
    void testBadRequest() {
        assertThat(ErrorCode.BAD_REQUEST.getCode()).isEqualTo(400);
        assertThat(ErrorCode.BAD_REQUEST.getDefaultMessage()).isEqualTo("请求参数错误");
    }

    @Test
    void testUnauthorized() {
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo(401);
        assertThat(ErrorCode.UNAUTHORIZED.getDefaultMessage()).isEqualTo("未授权访问");
    }

    @Test
    void testForbidden() {
        assertThat(ErrorCode.FORBIDDEN.getCode()).isEqualTo(403);
        assertThat(ErrorCode.FORBIDDEN.getDefaultMessage()).isEqualTo("禁止访问");
    }

    @Test
    void testNotFound() {
        assertThat(ErrorCode.NOT_FOUND.getCode()).isEqualTo(404);
        assertThat(ErrorCode.NOT_FOUND.getDefaultMessage()).isEqualTo("资源不存在");
    }

    @Test
    void testBusinessError() {
        assertThat(ErrorCode.BUSINESS_ERROR.getCode()).isEqualTo(400);
        assertThat(ErrorCode.BUSINESS_ERROR.getDefaultMessage()).isEqualTo("业务处理失败");
    }

    @Test
    void testValidationError() {
        assertThat(ErrorCode.VALIDATION_ERROR.getCode()).isEqualTo(400);
        assertThat(ErrorCode.VALIDATION_ERROR.getDefaultMessage()).isEqualTo("参数校验失败");
    }

    @Test
    void testInternalError() {
        assertThat(ErrorCode.INTERNAL_ERROR.getCode()).isEqualTo(500);
        assertThat(ErrorCode.INTERNAL_ERROR.getDefaultMessage()).isEqualTo("服务器内部错误");
    }

    @Test
    void testServiceUnavailable() {
        assertThat(ErrorCode.SERVICE_UNAVAILABLE.getCode()).isEqualTo(503);
        assertThat(ErrorCode.SERVICE_UNAVAILABLE.getDefaultMessage()).isEqualTo("服务暂不可用");
    }

    @Test
    void testAllErrorCodesHaveCode() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getCode()).isPositive();
            assertThat(code.getDefaultMessage()).isNotEmpty();
        }
    }
}