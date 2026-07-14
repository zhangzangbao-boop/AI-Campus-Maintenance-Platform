package com.qiyun.feign.dto;

/**
 * 统计数据响应DTO
 * 用于ops-service从repair-service获取统计数据
 */
public record StatsResponse(
    String status,
    String message,
    Object data
) {
    public static StatsResponse success(Object data) {
        return new StatsResponse("SUCCESS", null, data);
    }

    public static StatsResponse error(String message) {
        return new StatsResponse("ERROR", message, null);
    }
}