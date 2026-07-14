package com.qiyun.feign.dto;

/**
 * 用户摘要 DTO
 * 用于服务间共享用户基本信息，不包含敏感字段
 */
public record UserSummaryDto(
    String userId,
    String nickname,
    String role,
    boolean active,
    String contactPhone
) {}