package com.ligong.reportingcenter.dto;

import java.time.LocalDateTime;

public record RatingDto(
    Long ratingId,
    Integer score,
    String comment,
    String studentId,
    String studentName,  // 新增：学生姓名
    String staffId,
    String staffName,    // 新增：维修人员姓名
    Long repairOrderId,  // 新增：报修单ID
    Integer speedRating,
    Integer qualityRating,
    Integer attitudeRating,
    Boolean resolved,
    Boolean anonymous,
    LocalDateTime ratedAt
) {
}

