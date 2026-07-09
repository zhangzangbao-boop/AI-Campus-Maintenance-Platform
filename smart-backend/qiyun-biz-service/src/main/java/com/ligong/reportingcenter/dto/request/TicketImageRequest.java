package com.ligong.reportingcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketImageRequest(
    @NotBlank(message = "图片URL不能为空")
    @Size(max = 255, message = "图片URL过长")
    String imageUrl
) {
}

