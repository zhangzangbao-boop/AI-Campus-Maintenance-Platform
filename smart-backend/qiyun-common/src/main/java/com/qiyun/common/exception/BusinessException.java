package com.qiyun.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常类
 * 用于封装业务逻辑中的可预期异常
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;

    /**
     * 仅使用消息构造异常（默认使用 BAD_REQUEST 状态）
     *
     * @param message 异常消息
     */
    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 使用 HTTP 状态和消息构造异常
     *
     * @param status HTTP 状态码
     * @param message 异常消息
     */
    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.errorCode = mapToErrorCode(status);
    }

    /**
     * 使用 ErrorCode 构造异常
     *
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.status = HttpStatus.valueOf(errorCode.getCode());
        this.errorCode = errorCode;
    }

    /**
     * 使用 ErrorCode 和自定义消息构造异常
     *
     * @param errorCode 错误码
     * @param message 自定义消息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.status = HttpStatus.valueOf(errorCode.getCode());
        this.errorCode = errorCode;
    }

    /**
     * 使用 Throwable cause 构造异常
     *
     * @param message 异常消息
     * @param cause 原始异常
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.BAD_REQUEST;
        this.errorCode = ErrorCode.BUSINESS_ERROR;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 将 HTTP 状态映射到 ErrorCode
     */
    private ErrorCode mapToErrorCode(HttpStatus status) {
        switch (status) {
            case BAD_REQUEST:
                return ErrorCode.BAD_REQUEST;
            case UNAUTHORIZED:
                return ErrorCode.UNAUTHORIZED;
            case FORBIDDEN:
                return ErrorCode.FORBIDDEN;
            case NOT_FOUND:
                return ErrorCode.NOT_FOUND;
            case INTERNAL_SERVER_ERROR:
                return ErrorCode.INTERNAL_ERROR;
            case SERVICE_UNAVAILABLE:
                return ErrorCode.SERVICE_UNAVAILABLE;
            default:
                return ErrorCode.BUSINESS_ERROR;
        }
    }
}