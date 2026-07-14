package com.qiyun.repairservice.dto.request;

import com.qiyun.repairservice.domain.enums.TicketCommentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketCommentRequest(
    @NotBlank(message = "沟通内容不能为空")
    @Size(max = 1000, message = "沟通内容不能超过1000个字符")
    String content,
    TicketCommentType commentType,
    String imageUrl
) {
}