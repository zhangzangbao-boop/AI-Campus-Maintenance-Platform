package com.qiyun.repairservice.dto;

public record HistoricalRepairCaseDto(
    Long ticketId,
    String categoryName,
    Double similarity,
    String failureCause,
    String repairMethod,
    String materials,
    String result,
    Boolean fallback
) {
}
