package com.ligong.reportingcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TicketCreateRequest(
    @NotBlank(message = "学生ID不能为空")
    @Size(max = 50, message = "学生ID长度不能超过50")
    String studentId,

    @NotNull(message = "分类ID不能为空")
    Long categoryId,

    @NotBlank(message = "报修地点不能为空")
    @Size(max = 255, message = "报修地点长度不能超过255")
    String locationText,

    @NotBlank(message = "问题描述不能为空")
    String description,

    String priority,

    List<@NotBlank(message = "图片URL不能为空") @Size(max = 255, message = "图片URL过长") String> imageUrls
) {
}