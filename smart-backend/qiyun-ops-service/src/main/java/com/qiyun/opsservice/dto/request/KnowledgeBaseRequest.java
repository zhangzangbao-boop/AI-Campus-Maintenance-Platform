package com.qiyun.opsservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeBaseRequest(
    @NotBlank(message = "分类键不能为空")
    String categoryKey,

    @NotBlank(message = "标题不能为空")
    String title,

    @NotBlank(message = "内容不能为空")
    String content,

    String tags,

    Boolean enabled
) {}