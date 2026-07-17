package com.qiyun.repairservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.repairservice.domain.entity.Rating;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.FeedbackFollowUpStatus;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.dto.RatingDto;
import com.qiyun.repairservice.dto.request.FeedbackFollowUpUpdateRequest;
import com.qiyun.repairservice.repository.RatingRepository;
import com.qiyun.repairservice.repository.UserReferenceRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackFollowUpService {

    private final RatingRepository ratingRepository;
    private final UserReferenceRepository userReferenceRepository;
    private final NotificationService notificationService;
    private final RatingDtoMapper ratingDtoMapper;

    @Transactional
    public void markPendingIfNeeded(Long ratingId) {
        Rating rating = ratingRepository.findById(ratingId).orElse(null);
        if (rating == null || rating.getFollowUpStatus() != null || !requiresFollowUp(rating)) {
            return;
        }
        rating.setFollowUpStatus(FeedbackFollowUpStatus.PENDING);
        rating.setFollowUpUpdatedAt(LocalDateTime.now());
        ratingRepository.save(rating);
    }

    @Transactional
    public RatingDto updateFollowUp(Long ratingId, FeedbackFollowUpUpdateRequest request) {
        if (request == null || request.operatorId() == null || request.operatorId().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "operatorId is required");
        }
        FeedbackFollowUpStatus targetStatus = request.status() == null
            ? FeedbackFollowUpStatus.PROCESSING
            : request.status();
        Rating rating = ratingRepository.findById(ratingId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Feedback not found"));
        UserReference operator = userReferenceRepository.findByUserIdAndIsActiveTrue(request.operatorId())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Operator not found"));
        if (operator.getRole() != UserRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only ADMIN can update feedback follow-up");
        }
        if (rating.getFollowUpStatus() == null && !requiresFollowUp(rating)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Feedback does not require follow-up");
        }

        FeedbackFollowUpStatus previousStatus = rating.getFollowUpStatus();
        rating.setFollowUpStatus(targetStatus);
        rating.setFollowUpNote(cleanNote(request.note()));
        rating.setFollowUpOperator(operator);
        rating.setFollowUpUpdatedAt(LocalDateTime.now());
        ratingRepository.save(rating);

        if (targetStatus == FeedbackFollowUpStatus.RESOLVED && previousStatus != FeedbackFollowUpStatus.RESOLVED) {
            notifyStudentResolved(rating);
        }
        return ratingDtoMapper.toDto(rating);
    }

    private boolean requiresFollowUp(Rating rating) {
        return (rating.getScore() != null && rating.getScore() <= 2)
            || "NEGATIVE".equalsIgnoreCase(rating.getSentiment());
    }

    private String cleanNote(String note) {
        if (note == null) {
            return "";
        }
        String trimmed = note.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    private void notifyStudentResolved(Rating rating) {
        if (rating.getStudent() == null) {
            return;
        }
        String content = "Your feedback follow-up has been resolved.";
        if (rating.getFollowUpNote() != null && !rating.getFollowUpNote().isBlank()) {
            content += " Result: " + rating.getFollowUpNote();
        }
        notificationService.notifyUser(rating.getStudent(), "Feedback follow-up resolved", content, rating.getTicket());
    }
}
