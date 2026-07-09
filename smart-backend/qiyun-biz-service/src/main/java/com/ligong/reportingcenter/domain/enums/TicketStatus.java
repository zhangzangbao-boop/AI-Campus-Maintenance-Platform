package com.ligong.reportingcenter.domain.enums;

public enum TicketStatus {
    WAITING_ACCEPT("待受理"),
    IN_PROGRESS("处理中"),
    RESOLVED("已处理"),
    WAITING_FEEDBACK("待评价"),
    FEEDBACKED("已评价"),
    CLOSED("已关闭"),
    REJECTED("已驳回");

    private final String description;

    TicketStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

