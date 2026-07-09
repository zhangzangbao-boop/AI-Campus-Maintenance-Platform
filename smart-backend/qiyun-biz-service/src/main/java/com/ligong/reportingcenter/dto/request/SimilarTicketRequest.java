package com.ligong.reportingcenter.dto.request;

public record SimilarTicketRequest(
    String description,
    String locationText,
    String categoryKey,
    Integer limit
) {
}
