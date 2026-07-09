package com.ligong.reportingcenter.dto.request;

public record AiTicketAnalyzeRequest(
    String text,
    String locationText,
    String categoryKey
) {
}
