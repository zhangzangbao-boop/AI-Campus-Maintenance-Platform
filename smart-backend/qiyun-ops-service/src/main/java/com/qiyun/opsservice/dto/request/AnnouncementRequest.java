package com.qiyun.opsservice.dto.request;

import com.qiyun.opsservice.domain.enums.AnnouncementPriority;
import com.qiyun.opsservice.domain.enums.AnnouncementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record AnnouncementRequest(
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 200, message = "公告标题不能超过200个字符")
    String title,

    @NotBlank(message = "公告内容不能为空")
    String content,

    AnnouncementType type,
    AnnouncementPriority priority,
    LocalDateTime publishTime,
    LocalDateTime expireTime,
    Boolean pinned
) {
}
