package com.qiyun.repairservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudentCompletionRejectRequest(
    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason is too long")
    String reason
) {
}
