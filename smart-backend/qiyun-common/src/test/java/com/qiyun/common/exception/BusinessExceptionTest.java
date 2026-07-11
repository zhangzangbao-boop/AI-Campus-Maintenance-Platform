package com.qiyun.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * BusinessException 单元测试
 */
class BusinessExceptionTest {

    @Test
    void testConstructor_messageOnly() {
        BusinessException ex = new BusinessException("仅消息测试");

        assertThat(ex.getMessage()).isEqualTo("仅消息测试");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void testConstructor_statusAndMessage() {
        BusinessException ex = new BusinessException(HttpStatus.NOT_FOUND, "资源未找到");

        assertThat(ex.getMessage()).isEqualTo("资源未找到");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void testConstructor_errorCode() {
        BusinessException ex = new BusinessException(ErrorCode.FORBIDDEN);

        assertThat(ex.getMessage()).isEqualTo("禁止访问");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void testConstructor_errorCodeAndMessage() {
        BusinessException ex = new BusinessException(ErrorCode.VALIDATION_ERROR, "用户名长度不足");

        assertThat(ex.getMessage()).isEqualTo("用户名长度不足");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void testConstructor_messageAndCause() {
        Throwable cause = new RuntimeException("原始异常");
        BusinessException ex = new BusinessException("包装异常", cause);

        assertThat(ex.getMessage()).isEqualTo("包装异常");
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    void testStatusMapping_internalError() {
        BusinessException ex = new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "服务器错误");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void testStatusMapping_unauthorized() {
        BusinessException ex = new BusinessException(HttpStatus.UNAUTHORIZED, "未授权");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void testStatusMapping_serviceUnavailable() {
        BusinessException ex = new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "服务不可用");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void testStatusMapping_defaultBusinessError() {
        // 使用一个没有明确映射的状态码
        BusinessException ex = new BusinessException(HttpStatus.CONFLICT, "冲突");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    void testExceptionIsRuntimeException() {
        BusinessException ex = new BusinessException("测试");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}