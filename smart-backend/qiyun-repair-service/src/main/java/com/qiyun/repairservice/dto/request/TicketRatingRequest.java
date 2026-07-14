package com.qiyun.repairservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketRatingRequest(
    @NotBlank(message = "学生ID不能为空")
    @Size(max = 50, message = "学生ID长度不能超过50")
    String studentId,

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分不能小于1")
    @Max(value = 5, message = "评分不能大于5")
    Integer score,

    @Size(max = 1000, message = "评价内容过长")
    String comment,

    @Min(value = 1, message = "维修速度评分不能小于1")
    @Max(value = 5, message = "维修速度评分不能大于5")
    Integer speedRating,

    @Min(value = 1, message = "维修质量评分不能小于1")
    @Max(value = 5, message = "维修质量评分不能大于5")
    Integer qualityRating,

    @Min(value = 1, message = "服务态度评分不能小于1")
    @Max(value = 5, message = "服务态度评分不能大于5")
    Integer attitudeRating,

    Boolean resolved,

    Boolean anonymous
) {
}