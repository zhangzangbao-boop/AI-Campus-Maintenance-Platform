package com.qiyun.repairservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.repairservice.domain.entity.Rating;
import com.qiyun.repairservice.domain.enums.FeedbackFollowUpStatus;
import com.qiyun.repairservice.dto.RatingDto;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RatingDtoMapper {

    private final ObjectMapper objectMapper;

    public RatingDto toDto(Rating rating) {
        Long repairOrderId = rating.getTicket() != null ? rating.getTicket().getTicketId() : null;
        String studentName = rating.getStudent() != null
            ? firstText(rating.getStudent().getNickname(), rating.getStudent().getUserId())
            : null;
        String staffName = rating.getStaff() != null
            ? firstText(rating.getStaff().getNickname(), rating.getStaff().getUserId())
            : null;
        String followUpOperatorName = rating.getFollowUpOperator() != null
            ? firstText(rating.getFollowUpOperator().getNickname(), rating.getFollowUpOperator().getUserId())
            : null;

        return new RatingDto(
            rating.getRatingId(),
            rating.getScore(),
            rating.getComment(),
            rating.getStudent() != null ? rating.getStudent().getUserId() : null,
            studentName,
            rating.getStaff() != null ? rating.getStaff().getUserId() : null,
            staffName,
            repairOrderId,
            rating.getSpeedRating(),
            rating.getQualityRating(),
            rating.getAttitudeRating(),
            rating.getResolved(),
            rating.getAnonymous(),
            rating.getRatedAt(),
            rating.getSentiment(),
            rating.getSentimentScore(),
            parseKeywords(rating.getSentimentKeywords()),
            rating.getSentimentSummary(),
            rating.getSentimentAnalyzedAt(),
            normalizeFollowUpStatus(rating),
            rating.getFollowUpNote(),
            rating.getFollowUpOperator() != null ? rating.getFollowUpOperator().getUserId() : null,
            followUpOperatorName,
            rating.getFollowUpUpdatedAt()
        );
    }

    private String normalizeFollowUpStatus(Rating rating) {
        FeedbackFollowUpStatus status = rating.getFollowUpStatus();
        if (status == FeedbackFollowUpStatus.HANDLED || status == FeedbackFollowUpStatus.RESOLVED) {
            return FeedbackFollowUpStatus.HANDLED.name();
        }
        if (status == FeedbackFollowUpStatus.PENDING || status == FeedbackFollowUpStatus.PROCESSING) {
            return FeedbackFollowUpStatus.PENDING.name();
        }
        if (status == FeedbackFollowUpStatus.NO_NEED) {
            return FeedbackFollowUpStatus.NO_NEED.name();
        }
        boolean requiresFollowUp = (rating.getScore() != null && rating.getScore() <= 2)
            || "NEGATIVE".equalsIgnoreCase(rating.getSentiment())
            || !Boolean.TRUE.equals(rating.getResolved());
        return requiresFollowUp ? FeedbackFollowUpStatus.PENDING.name() : FeedbackFollowUpStatus.NO_NEED.name();
    }

    private List<String> parseKeywords(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return java.util.Arrays.stream(value.split("[,，、\\s]+"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
