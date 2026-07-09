package com.ligong.reportingcenter.dto.request;

public record SystemConfigRequest(
    String configValue,
    String description
) {
}
