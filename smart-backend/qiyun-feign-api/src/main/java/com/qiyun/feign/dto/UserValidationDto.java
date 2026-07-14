package com.qiyun.feign.dto;

/**
 * 用户验证 DTO
 * 用于验证用户状态
 */
public record UserValidationDto(
    boolean exists,
    boolean active,
    String role
) {}