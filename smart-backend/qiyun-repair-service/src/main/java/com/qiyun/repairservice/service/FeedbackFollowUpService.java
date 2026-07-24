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
    private final NotificationPushService notificationPushService;
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
        FeedbackFollowUpStatus targetStatus = normalizeTargetStatus(request.status());
        Rating rating = ratingRepository.findById(ratingId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Feedback not found"));
        UserReference operator = userReferenceRepository.findByUserIdAndIsActiveTrue(request.operatorId())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Operator not found"));
        if (operator.getRole() != UserRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only ADMIN can update feedback follow-up");
        }
        if (!requiresFollowUp(rating)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Feedback does not require follow-up");
        }
        if (targetStatus != FeedbackFollowUpStatus.HANDLED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Follow-up can only be submitted as HANDLED");
        }

        String cleanedNote = cleanNote(request.note());
        if (cleanedNote.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "follow-up note is required");
        }

        FeedbackFollowUpStatus previousStatus = normalizeStoredStatus(rating.getFollowUpStatus(), rating);
        rating.setFollowUpStatus(targetStatus);
        rating.setFollowUpNote(cleanedNote);
        rating.setFollowUpOperator(operator);
        rating.setFollowUpUpdatedAt(LocalDateTime.now());
        ratingRepository.save(rating);

        if (targetStatus == FeedbackFollowUpStatus.HANDLED && previousStatus != FeedbackFollowUpStatus.HANDLED) {
            notifyStudentHandled(rating);
        }
        return ratingDtoMapper.toDto(rating);
    }

    public boolean requiresFollowUp(Rating rating) {
        return (rating.getScore() != null && rating.getScore() <= 2)
            || "NEGATIVE".equalsIgnoreCase(rating.getSentiment())
            || !Boolean.TRUE.equals(rating.getResolved());
    }

    public FeedbackFollowUpStatus normalizeStoredStatus(FeedbackFollowUpStatus status, Rating rating) {
        if (status == FeedbackFollowUpStatus.HANDLED || status == FeedbackFollowUpStatus.RESOLVED) {
            return FeedbackFollowUpStatus.HANDLED;
        }
        if (status == FeedbackFollowUpStatus.PENDING || status == FeedbackFollowUpStatus.PROCESSING) {
            return FeedbackFollowUpStatus.PENDING;
        }
        if (status == FeedbackFollowUpStatus.NO_NEED) {
            return FeedbackFollowUpStatus.NO_NEED;
        }
        return requiresFollowUp(rating) ? FeedbackFollowUpStatus.PENDING : FeedbackFollowUpStatus.NO_NEED;
    }

    private FeedbackFollowUpStatus normalizeTargetStatus(FeedbackFollowUpStatus status) {
        if (status == null || status == FeedbackFollowUpStatus.HANDLED || status == FeedbackFollowUpStatus.RESOLVED) {
            return FeedbackFollowUpStatus.HANDLED;
        }
        if (status == FeedbackFollowUpStatus.PENDING || status == FeedbackFollowUpStatus.PROCESSING) {
            return FeedbackFollowUpStatus.PENDING;
        }
        return status;
    }

    private String cleanNote(String note) {
        if (note == null) {
            return "";
        }
        String trimmed = note.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    private void notifyStudentHandled(Rating rating) {
        if (rating.getStudent() == null) {
            return;
        }
        String content = "管理员已处理您对工单的反馈，请进入历史工单详情查看回访说明。";
        if (rating.getFollowUpNote() != null && !rating.getFollowUpNote().isBlank()) {
            content += "处理说明：" + rating.getFollowUpNote();
        }
        notificationPushService.notifyAndPush(rating.getStudent(), "反馈回访已处理", content, rating.getTicket());
    }
}
