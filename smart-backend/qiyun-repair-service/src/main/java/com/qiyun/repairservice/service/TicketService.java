package com.qiyun.repairservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.repairservice.domain.entity.Category;
import com.qiyun.repairservice.domain.entity.Rating;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.TicketImage;
import com.qiyun.repairservice.domain.entity.TicketStatusLog;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.dto.*;
import com.qiyun.repairservice.dto.request.AiTicketAnalysisCorrectionRequest;
import com.qiyun.repairservice.dto.request.TicketAssignRequest;
import com.qiyun.repairservice.dto.request.TicketCreateRequest;
import com.qiyun.repairservice.dto.request.TicketRatingRequest;
import com.qiyun.repairservice.dto.request.TicketStatusUpdateRequest;
import com.qiyun.repairservice.repository.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<TicketStatus> STAFF_OVERVIEW_STATUSES = EnumSet.of(
        TicketStatus.WAITING_ACCEPT,
        TicketStatus.IN_PROGRESS,
        TicketStatus.RESOLVED
    );
    private static final Set<TicketStatus> TODAY_TASK_STATUSES = EnumSet.of(TicketStatus.WAITING_ACCEPT, TicketStatus.IN_PROGRESS);
    private static final Set<TicketStatus> PROCESSING_TASK_STATUSES = EnumSet.of(TicketStatus.IN_PROGRESS);
    private static final Set<TicketStatus> ACTIVE_TASK_STATUSES = EnumSet.of(TicketStatus.WAITING_ACCEPT, TicketStatus.IN_PROGRESS);
    private static final Set<TicketStatus> RECORD_TASK_STATUSES = EnumSet.of(
        TicketStatus.RESOLVED,
        TicketStatus.WAITING_FEEDBACK,
        TicketStatus.FEEDBACKED,
        TicketStatus.CLOSED
    );
    private static final Set<TicketStatus> STUDENT_PROGRESS_STATUSES = EnumSet.of(
        TicketStatus.WAITING_ACCEPT,
        TicketStatus.IN_PROGRESS,
        TicketStatus.RESOLVED
    );
    private static final Set<TicketStatus> STUDENT_FEEDBACK_STATUSES = EnumSet.of(TicketStatus.WAITING_FEEDBACK);
    private static final Set<TicketStatus> STUDENT_HISTORY_STATUSES = EnumSet.of(
        TicketStatus.FEEDBACKED,
        TicketStatus.CLOSED,
        TicketStatus.REJECTED
    );

    private final TicketRepository ticketRepository;
    private final TicketImageRepository imageRepository;
    private final TicketStatusLogRepository statusLogRepository;
    private final RatingRepository ratingRepository;
    private final UserReferenceRepository userReferenceRepository;
    private final CategoryService categoryService;
    private final NotificationService notificationService;
    private final NotificationPushService notificationPushService;
    private final FileStorageService fileStorageService;
    private final AiAnalysisIntegrationService aiAnalysisIntegrationService;
    private final TicketCompletionSummaryService ticketCompletionSummaryService;
    private final FeedbackSentimentAnalysisService feedbackSentimentAnalysisService;
    private final FeedbackFollowUpService feedbackFollowUpService;
    private final RatingDtoMapper ratingDtoMapper;
    private final RepairRuleConfigService repairRuleConfigService;
    private final HistoricalRepairCaseService historicalRepairCaseService;
    private final AuditEventPublisher auditEventPublisher;

    @Transactional
    public TicketDetailDto createTicket(TicketCreateRequest request, List<MultipartFile> images) {
        UserReference student = userReferenceRepository.findByUserIdAndIsActiveTrue(request.studentId())
            .orElseThrow(() -> new BusinessException("用户不存在或已禁用"));
        if (student.getRole() != UserRole.STUDENT) {
            throw new BusinessException("仅学生账号可以提交报修单");
        }

        Category category = categoryService.getById(request.categoryId());
        if (category == null) {
            throw new BusinessException("指定的分类不存在");
        }

        RepairTicket ticket = new RepairTicket();
        ticket.setStudent(student);
        ticket.setCategory(category);
        ticket.setLocationText(request.locationText());
        ticket.setDescription(request.description());
        ticket.setStatus(TicketStatus.WAITING_ACCEPT);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setPriority(request.priority() != null ? request.priority() : "medium");

        ticket = ticketRepository.save(ticket);
        ticketRepository.flush();

        appendStatusLog(ticket, null, TicketStatus.WAITING_ACCEPT, student);

        if (images != null && !images.isEmpty()) {
            for (String imageUrl : fileStorageService.storeImages(images)) {
                TicketImage ticketImage = new TicketImage();
                ticketImage.setTicket(ticket);
                ticketImage.setImageUrl(imageUrl);
                ticketImage.setUploadedAt(LocalDateTime.now());
                imageRepository.save(ticketImage);
            }
        }

        ticketRepository.flush();

        // AI analysis (non-blocking)
        try {
            aiAnalysisIntegrationService.analyzeAndSave(ticket.getTicketId(), ticket.getDescription(), ticket.getLocationText());
        } catch (Exception e) {
            log.warn("AI分析调用失败: ticketId={}, error={}", ticket.getTicketId(), e.getMessage());
        }

        notifyAdminsNewTicket(ticket);
        return toDetailDto(ticket);
    }

    @Transactional
    public TicketDetailDto assignTicket(Long ticketId, TicketAssignRequest request) {
        try {
            RepairTicket ticket = ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
            UserReference operator = userReferenceRepository.findByUserIdAndIsActiveTrue(request.operatorId())
                .orElseThrow(() -> new BusinessException("操作员不存在或已禁用"));
            UserReference staff = userReferenceRepository.findByUserIdAndIsActiveTrue(request.staffId())
                .orElseThrow(() -> new BusinessException("维修工不存在或已禁用"));

            if (staff.getRole() != UserRole.STAFF) {
                throw new BusinessException("仅维修工角色可以被分配");
            }
            if (ticket.getStatus() != TicketStatus.WAITING_ACCEPT && ticket.getStatus() != TicketStatus.IN_PROGRESS) {
                throw new BusinessException("当前工单状态不允许派单或转派");
            }

            UserReference oldStaff = ticket.getStaff();
            ticket.setStaff(staff);
            ticket.setAssignedAt(LocalDateTime.now());
            TicketStatus oldStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            appendStatusLog(ticket, oldStatus, TicketStatus.IN_PROGRESS, operator);

            if (oldStaff != null && !oldStaff.getUserId().equals(staff.getUserId())) {
                notificationService.notifyUser(oldStaff, "工单已转派",
                    "工单 #" + ticket.getTicketId() + " 已转派给 " + safeName(staff) + " 继续处理。", ticket);
            }
            notifyTicketAssigned(ticket, staff);
            auditEventPublisher.record(operator.getUserId(), "工单管理", "派单/转派", "REPAIR_TICKET",
                String.valueOf(ticket.getTicketId()),
                "工单#" + ticket.getTicketId() + " 指派给维修工 " + staff.getUserId()
                    + "，原维修工=" + (oldStaff == null ? "无" : oldStaff.getUserId()));
            return toDetailDto(ticket);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("工单已被其他用户修改，请刷新后重试");
        }
    }

    @Transactional
    public TicketDetailDto updateStatus(Long ticketId, TicketStatusUpdateRequest request) {
        try {
            RepairTicket ticket = ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
            UserReference operator = userReferenceRepository.findByUserIdAndIsActiveTrue(request.operatorId())
                .orElseThrow(() -> new BusinessException("操作员不存在或已禁用"));

            TicketStatus oldStatus = ticket.getStatus();
            TicketStatus newStatus = request.newStatus();

            if (!isValidStatusTransition(oldStatus, newStatus)) {
                throw new BusinessException("不允许从 " + oldStatus + " 转换到 " + newStatus);
            }

            if (newStatus == TicketStatus.REJECTED) {
                if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
                    throw new BusinessException("驳回时必须填写理由");
                }
                ticket.setRejectionReason(request.rejectionReason());
                ticket.setStaff(null);
                ticket.setAssignedAt(null);
                ticket.setCompletedAt(null);
                ticket.setClosedAt(LocalDateTime.now());
            } else if (newStatus == TicketStatus.RESOLVED) {
                ticket.setCompletedAt(LocalDateTime.now());
            } else if (newStatus == TicketStatus.WAITING_FEEDBACK) {
                if (ticket.getCompletedAt() == null) {
                    ticket.setCompletedAt(LocalDateTime.now());
                }
            } else if (newStatus == TicketStatus.CLOSED) {
                ticket.setClosedAt(LocalDateTime.now());
            }

            ticket.setStatus(newStatus);
            appendStatusLog(ticket, oldStatus, newStatus, operator);
            triggerCompletionSummaryIfNeeded(ticket);
            auditEventPublisher.record(operator.getUserId(), "工单管理", "修改状态", "REPAIR_TICKET",
                String.valueOf(ticket.getTicketId()),
                "工单#" + ticket.getTicketId() + " 状态 " + oldStatus + " -> " + newStatus);
            return toDetailDto(ticket);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("工单已被其他用户修改，请刷新后重试");
        }
    }

    @Transactional
    public TicketDetailDto rateTicket(Long ticketId, TicketRatingRequest request) {
        try {
            RepairTicket ticket = ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));

            if (!Objects.equals(ticket.getStudent().getUserId(), request.studentId())) {
                throw new BusinessException("仅提交该报修单的学生可以评价");
            }
            if (ratingRepository.existsByTicket(ticket)) {
                throw new BusinessException("该报修单已评价");
            }

            TicketStatus status = ticket.getStatus();
            if (status != TicketStatus.WAITING_FEEDBACK) {
                throw new BusinessException("当前状态不可评价");
            }

            Rating rating = new Rating();
            rating.setTicket(ticket);
            rating.setStudent(ticket.getStudent());
            rating.setStaff(ticket.getStaff());
            rating.setScore(request.score());
            rating.setComment(request.comment());
            rating.setSpeedRating(request.speedRating());
            rating.setQualityRating(request.qualityRating());
            rating.setAttitudeRating(request.attitudeRating());
            rating.setResolved(request.resolved());
            rating.setAnonymous(Boolean.TRUE.equals(request.anonymous()));
            rating = ratingRepository.save(rating);
            feedbackFollowUpService.markPendingIfNeeded(rating.getRatingId());
            triggerFeedbackSentimentIfNeeded(rating.getRatingId());

            TicketStatus oldStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.FEEDBACKED);
            ticket.setClosedAt(LocalDateTime.now());
            appendStatusLog(ticket, oldStatus, TicketStatus.FEEDBACKED, ticket.getStudent());
            return toDetailDto(ticket);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("工单已被其他用户修改，请刷新后重试");
        }
    }

    @Transactional
    public void deleteTicket(Long ticketId, String studentId) {
        RepairTicket ticket = ticketRepository.findByIdWithLock(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
        if (!ticket.getStudent().getUserId().equals(studentId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能删除自己提交的报修单");
        }
        if (ticket.getStatus() != TicketStatus.WAITING_ACCEPT) {
            throw new BusinessException("仅待受理状态的报修单可以删除");
        }
        ticketRepository.delete(ticket);
        triggerHistoricalRepairCaseDelete(ticketId);
    }

    @Transactional
    public TicketDetailDto getTicketDetail(Long ticketId) {
        return getTicketDetail(ticketId, false);
    }

    @Transactional
    public TicketDetailDto getTicketDetail(Long ticketId, boolean includeAdminAiFields) {
        RepairTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Ticket not found"));
        return toDetailDto(ticket, includeAdminAiFields);
    }

    @Transactional
    public AiTicketAnalysisViewDto correctAiAnalysis(Long ticketId, AiTicketAnalysisCorrectionRequest request, String operatorId) {
        ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Ticket not found"));
        AiTicketAnalysisViewDto analysis = aiAnalysisIntegrationService.correctAnalysis(ticketId, request, operatorId);
        auditEventPublisher.record(operatorId, "AI人工修正", "修正工单AI分析", "REPAIR_TICKET",
            String.valueOf(ticketId),
            "工单#" + ticketId + " AI分析已人工修正，原因=" + request.reason());
        return analysis;
    }

    @Transactional(readOnly = true)
    public List<TicketSummaryDto> listByStudent(String studentId) {
        UserReference student = userReferenceRepository.findByUserId(studentId)
            .orElseThrow(() -> new BusinessException("用户不存在"));
        return ticketRepository.findByStudent(student).stream()
            .sorted(Comparator.comparing(RepairTicket::getCreatedAt).reversed())
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TicketSummaryDto> listAll(boolean includeDeleted) {
        return ticketRepository.findAll().stream()
            .filter(t -> includeDeleted || !Boolean.TRUE.equals(t.getDeleted()))
            .sorted(Comparator.comparing(RepairTicket::getCreatedAt).reversed())
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listStudentTicketsPage(
        String studentId,
        TicketStatus status,
        String scope,
        String category,
        String priority,
        String keyword,
        int page,
        int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Set<TicketStatus> allowedStatuses = allowedStudentStatuses(scope);
        if (status != null && allowedStatuses != null && !allowedStatuses.contains(status)) {
            return studentTicketPageResult(new PageImpl<>(List.of(), pageable, 0), safePage, safeSize, studentId);
        }

        Page<RepairTicket> ticketPage = ticketRepository.findAll((Specification<RepairTicket>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("student").get("userId"), studentId));
            predicates.add(cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted"))));
            if (allowedStatuses != null) {
                predicates.add(root.get("status").in(allowedStatuses));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            addCommonTicketFilters(predicates, root, cb, category, priority, keyword);
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        return studentTicketPageResult(ticketPage, safePage, safeSize, studentId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listAdminTicketsPage(
        TicketStatus status,
        String category,
        String keyword,
        boolean includeDeleted,
        int page,
        int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<RepairTicket> ticketPage = ticketRepository.findAll((Specification<RepairTicket>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeDeleted) {
                predicates.add(cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted"))));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            addCommonTicketFilters(predicates, root, cb, category, null, keyword);
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        return staffTaskPageResult(ticketPage, safePage, safeSize);
    }

    private Set<TicketStatus> allowedStudentStatuses(String scope) {
        if (scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope)) {
            return null;
        }
        return switch (scope.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "progress", "active" -> STUDENT_PROGRESS_STATUSES;
            case "feedback", "to-evaluate", "waiting-feedback" -> STUDENT_FEEDBACK_STATUSES;
            case "history", "completed", "records" -> STUDENT_HISTORY_STATUSES;
            default -> null;
        };
    }

    private void addCommonTicketFilters(
        List<Predicate> predicates,
        Root<RepairTicket> root,
        CriteriaBuilder cb,
        String category,
        String priority,
        String keyword
    ) {
        if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
            predicates.add(cb.equal(root.get("category").get("categoryName"), category));
        }
        if (priority != null && !priority.isBlank() && !"all".equalsIgnoreCase(priority)) {
            predicates.add(cb.equal(root.get("priority"), priority));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(cb.or(
                cb.like(cb.lower(root.get("locationText")), pattern),
                cb.like(cb.lower(root.get("description")), pattern),
                cb.like(cb.lower(root.get("student").get("userId")), pattern),
                cb.like(cb.lower(root.get("student").get("nickname")), pattern)
            ));
        }
    }

    @Transactional(readOnly = true)
    public List<StaffRecommendationDto> recommendStaffForTicket(Long ticketId) {
        RepairTicket targetTicket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
        String targetCategory = targetTicket.getCategory() != null ? targetTicket.getCategory().getCategoryName() : null;
        String targetLocation = targetTicket.getLocationText();

        List<UserReference> staffUsers = userReferenceRepository.findByRoleAndIsActiveTrue(UserRole.STAFF);
        return staffUsers.stream()
            .map(staff -> buildStaffRecommendation(staff, targetCategory, targetLocation))
            .sorted(Comparator.comparingDouble(StaffRecommendationDto::score).reversed())
            .toList();
    }

    @Transactional
    public TicketDetailDto acceptTicket(Long ticketId, String staffId) {
        RepairTicket ticket = ticketRepository.findByIdWithLock(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
        UserReference staff = userReferenceRepository.findByUserIdAndIsActiveTrue(staffId)
            .orElseThrow(() -> new BusinessException("用户不存在或已禁用"));

        if (staff.getRole() != UserRole.STAFF) {
            throw new BusinessException("仅维修工角色可以接单");
        }
        if (ticket.getStatus() != TicketStatus.WAITING_ACCEPT) {
            throw new BusinessException("当前工单状态不允许接单");
        }
        if (ticket.getStaff() != null && !Objects.equals(ticket.getStaff().getUserId(), staffId)) {
            throw new BusinessException("该工单已被分配");
        }

        TicketStatus oldStatus = ticket.getStatus();
        if (ticket.getStaff() == null) {
            ticket.setStaff(staff);
        }
        if (ticket.getAssignedAt() == null) {
            ticket.setAssignedAt(LocalDateTime.now());
        }
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        appendStatusLog(ticket, oldStatus, TicketStatus.IN_PROGRESS, staff);
        notifyTicketAssigned(ticket, staff);
        return toDetailDto(ticket);
    }

    @Transactional
    public TicketDetailDto resolveTicket(Long ticketId, String staffId, String repairNotes) {
        RepairTicket ticket = ticketRepository.findByIdWithLock(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
        UserReference staff = userReferenceRepository.findByUserId(staffId)
            .orElseThrow(() -> new BusinessException("用户不存在"));

        if (ticket.getStaff() == null || !ticket.getStaff().getUserId().equals(staffId)) {
            throw new BusinessException("只能完成分配给自己的工单");
        }
        if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
            throw new BusinessException("当前工单状态不允许完成");
        }

        if (repairNotes != null && !repairNotes.isBlank()) {
            ticket.setRepairNotes(repairNotes);
        }
        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setCompletedAt(LocalDateTime.now());
        ticket.setStudentConfirmedAt(null);
        ticket.setStudentRejectionReason(null);
        appendStatusLog(ticket, oldStatus, TicketStatus.RESOLVED, staff);
        notifyStudentToConfirm(ticket);
        triggerCompletionSummaryIfNeeded(ticket);
        return toDetailDto(ticket);
    }

    @Transactional
    public TicketDetailDto confirmCompletion(Long ticketId, String studentId) {
        RepairTicket ticket = ticketRepository.findByIdWithLock(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Ticket not found"));
        UserReference student = userReferenceRepository.findByUserIdAndIsActiveTrue(studentId)
            .orElseThrow(() -> new BusinessException("User not found"));

        assertStudentOwnsTicket(ticket, studentId);
        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            throw new BusinessException("Only resolved tickets can be confirmed");
        }

        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.WAITING_FEEDBACK);
        ticket.setStudentConfirmedAt(LocalDateTime.now());
        ticket.setStudentRejectionReason(null);
        appendStatusLog(ticket, oldStatus, TicketStatus.WAITING_FEEDBACK, student);
        notifyStaffCompletionConfirmed(ticket);
        triggerCompletionSummaryIfNeeded(ticket);
        triggerHistoricalRepairCaseSync(ticket.getTicketId());
        return toDetailDto(ticket);
    }

    @Transactional
    public TicketDetailDto rejectCompletion(Long ticketId, String studentId, String reason) {
        RepairTicket ticket = ticketRepository.findByIdWithLock(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Ticket not found"));
        UserReference student = userReferenceRepository.findByUserIdAndIsActiveTrue(studentId)
            .orElseThrow(() -> new BusinessException("User not found"));

        assertStudentOwnsTicket(ticket, studentId);
        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            throw new BusinessException("Only resolved tickets can be returned to processing");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isBlank()) {
            throw new BusinessException("Reason is required when the issue is not resolved");
        }
        if (cleanReason.length() > 500) {
            cleanReason = cleanReason.substring(0, 500);
        }

        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.WAITING_ACCEPT);
        ticket.setCompletedAt(null);
        ticket.setStudentConfirmedAt(null);
        ticket.setStudentRejectionReason(cleanReason);
        appendStatusLog(ticket, oldStatus, TicketStatus.WAITING_ACCEPT, student);
        notifyStaffCompletionRejected(ticket, cleanReason);
        return toDetailDto(ticket);
    }

    @Transactional
    public CompletionSummaryDto regenerateCompletionSummary(Long ticketId) {
        RepairTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
        if (!isCompletionSummaryStatus(ticket.getStatus())) {
            throw new BusinessException("仅已完成或已关闭的工单可重新生成完成总结");
        }
        return ticketCompletionSummaryService.regenerate(ticketId);
    }

    @Transactional(readOnly = true)
    public List<HistoricalRepairCaseDto> recommendHistoricalCases(Long ticketId) {
        return historicalRepairCaseService.recommendForTicket(ticketId, 5);
    }

    @Transactional(readOnly = true)
    public int rebuildHistoricalCaseIndex() {
        return historicalRepairCaseService.rebuildIndex();
    }

    @Transactional(readOnly = true)
    public int rebuildHistoricalCaseIndex(String operatorId) {
        int count = historicalRepairCaseService.rebuildIndex();
        auditEventPublisher.record(operatorId, "历史案例索引", "重建历史维修案例索引", "HISTORICAL_REPAIR_CASE",
            null, "重建历史维修案例向量索引，同步数量=" + count);
        return count;
    }

    @Transactional(readOnly = true)
    public StaffDashboardDto getStaffDashboard(String staffId) {
        Long pendingCount = ticketRepository.countByStaffIdAndStatus(staffId, "WAITING_ACCEPT");
        Long inProgressCount = ticketRepository.countByStaffIdAndStatus(staffId, "IN_PROGRESS");
        Long completedCount = ticketRepository.countCompletedTasksByStaffId(staffId);

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        List<RepairTicket> staffTickets = ticketRepository.findAll((Specification<RepairTicket>) (root, query, cb) -> cb.and(
            cb.equal(root.get("staff").get("userId"), staffId),
            cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted")))
        ));
        Long totalTaskCount = (long) staffTickets.size();
        Long awaitingFeedbackCount = staffTickets.stream()
            .filter(ticket -> ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.WAITING_FEEDBACK)
            .count();
        Long finishedCount = staffTickets.stream()
            .filter(ticket -> ticket.getStatus() == TicketStatus.FEEDBACKED || ticket.getStatus() == TicketStatus.CLOSED)
            .count();
        Long todayTaskCount = staffTickets.stream()
            .filter(this::isActiveStaffTask)
            .filter(ticket -> isTodayStaffTask(ticket, todayStart, todayEnd))
            .count();
        Long highPriorityCount = staffTickets.stream()
            .filter(this::isActiveStaffTask)
            .filter(this::isHighPriority)
            .count();
        Long slaAlertCount = staffTickets.stream()
            .filter(this::isActiveStaffTask)
            .filter(ticket -> isHighPriority(ticket) || isOverdueStaffTask(ticket, now))
            .count();
        Long todayCompleted = ticketRepository.countCompletedByStaffIdAndCompletedAtBetween(staffId, todayStart, todayEnd);

        LocalDateTime weekStart = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusWeeks(1);
        Long weekCompleted = ticketRepository.countCompletedByStaffIdAndCompletedAtBetween(staffId, weekStart, weekEnd);

        LocalDateTime monthStart = now.toLocalDate().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        LocalDateTime monthEnd = now.toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth()).atStartOfDay();
        Long monthCompleted = ticketRepository.countCompletedByStaffIdAndCompletedAtBetween(staffId, monthStart, monthEnd);

        Double avgRating = ticketRepository.findAverageRatingByStaffId(staffId);
        if (avgRating == null) avgRating = 0.0;

        Long totalRatingCount = ticketRepository.countRatingsByStaffId(staffId);
        if (totalRatingCount == null) totalRatingCount = 0L;

        Double avgProcessingHours = ticketRepository.findAverageProcessingHoursByStaffId(staffId);
        if (avgProcessingHours == null) avgProcessingHours = 0.0;

        Map<String, Long> priorityDistribution = new HashMap<>();
        List<Object[]> priorityStats = ticketRepository.findPriorityDistributionByStaffId(staffId);
        for (Object[] row : priorityStats) {
            String priority = row[0] != null ? row[0].toString() : "未设置";
            Long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            priorityDistribution.put(priority, count);
        }

        Map<String, Long> categoryDistribution = new HashMap<>();
        List<Object[]> categoryStats = ticketRepository.findCategoryDistributionByStaffId(staffId);
        for (Object[] row : categoryStats) {
            String category = row[0] != null ? row[0].toString() : "未分类";
            Long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            categoryDistribution.put(category, count);
        }

        return new StaffDashboardDto(
            totalTaskCount,
            pendingCount != null ? pendingCount : 0L,
            inProgressCount != null ? inProgressCount : 0L,
            awaitingFeedbackCount,
            finishedCount,
            completedCount != null ? completedCount : 0L,
            todayTaskCount,
            highPriorityCount,
            slaAlertCount,
            todayCompleted != null ? todayCompleted : 0L,
            weekCompleted != null ? weekCompleted : 0L,
            monthCompleted != null ? monthCompleted : 0L,
            avgRating,
            totalRatingCount,
            priorityDistribution,
            categoryDistribution,
            avgProcessingHours
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listStaffTasksPage(String staffId, TicketStatus status, int page, int size) {
        return listStaffTasksPage(staffId, status, null, null, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listStaffTasksPage(
        String staffId,
        TicketStatus status,
        String scope,
        String category,
        String priority,
        String keyword,
        int page,
        int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "assignedAt", "createdAt"));

        Set<TicketStatus> allowedStatuses = allowedStaffTaskStatuses(scope);
        if (status != null && allowedStatuses != null && !allowedStatuses.contains(status)) {
            return staffTaskPageResult(new PageImpl<>(List.of(), pageable, 0), safePage, safeSize);
        }

        Page<RepairTicket> ticketPage = ticketRepository.findAll((Specification<RepairTicket>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("staff").get("userId"), staffId));
            predicates.add(cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted"))));
            String normalizedScope = normalizeScope(scope);

            if (allowedStatuses != null) {
                predicates.add(root.get("status").in(allowedStatuses));
            }
            if (status == TicketStatus.CLOSED && "records".equals(normalizedScope)) {
                predicates.add(root.get("status").in(EnumSet.of(TicketStatus.FEEDBACKED, TicketStatus.CLOSED)));
            } else if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if ("today".equals(normalizedScope)) {
                LocalDate today = LocalDate.now(BUSINESS_ZONE);
                LocalDateTime start = today.atStartOfDay();
                LocalDateTime end = today.plusDays(1).atStartOfDay();
                predicates.add(todayStaffTaskPredicate(root, query, cb, start, end));
            } else if ("high-priority".equals(normalizedScope)) {
                predicates.add(cb.equal(cb.lower(root.get("priority")), "high"));
            } else if ("overdue".equals(normalizedScope)) {
                predicates.add(overdueStaffTaskPredicate(root, cb, LocalDateTime.now(BUSINESS_ZONE)));
            }
            if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
                predicates.add(cb.equal(root.get("category").get("categoryName"), category));
            }
            if (priority != null && !priority.isBlank() && !"all".equalsIgnoreCase(priority)) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("locationText")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("student").get("userId")), pattern),
                    cb.like(cb.lower(root.get("student").get("nickname")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        return staffTaskPageResult(ticketPage, safePage, safeSize);
    }

    private Set<TicketStatus> allowedStaffTaskStatuses(String scope) {
        String normalizedScope = normalizeScope(scope);
        if (normalizedScope == null || "all".equals(normalizedScope)) {
            return STAFF_OVERVIEW_STATUSES;
        }
        return switch (normalizedScope) {
            case "today" -> TODAY_TASK_STATUSES;
            case "processing" -> PROCESSING_TASK_STATUSES;
            case "records" -> RECORD_TASK_STATUSES;
            case "high-priority", "overdue" -> ACTIVE_TASK_STATUSES;
            default -> null;
        };
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return null;
        }
        return scope.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private Predicate todayStaffTaskPredicate(
        Root<RepairTicket> root,
        jakarta.persistence.criteria.CriteriaQuery<?> query,
        CriteriaBuilder cb,
        LocalDateTime start,
        LocalDateTime end
    ) {
        Subquery<Long> returnedToday = query.subquery(Long.class);
        Root<TicketStatusLog> log = returnedToday.from(TicketStatusLog.class);
        returnedToday.select(cb.literal(1L)).where(
            cb.equal(log.get("ticket"), root),
            cb.equal(log.get("oldStatus"), TicketStatus.RESOLVED),
            cb.equal(log.get("newStatus"), TicketStatus.WAITING_ACCEPT),
            cb.greaterThanOrEqualTo(log.get("logTime"), start),
            cb.lessThan(log.get("logTime"), end)
        );
        return cb.or(
            cb.and(cb.greaterThanOrEqualTo(root.get("assignedAt"), start), cb.lessThan(root.get("assignedAt"), end)),
            cb.and(cb.greaterThanOrEqualTo(root.get("estimatedCompletionTime"), start), cb.lessThan(root.get("estimatedCompletionTime"), end)),
            cb.exists(returnedToday)
        );
    }

    private Predicate overdueStaffTaskPredicate(Root<RepairTicket> root, CriteriaBuilder cb, LocalDateTime now) {
        LocalDateTime fallbackDeadline = now.minusDays(2);
        return cb.lessThan(root.get("createdAt"), fallbackDeadline);
    }

    private boolean isActiveStaffTask(RepairTicket ticket) {
        return ticket.getStatus() == TicketStatus.WAITING_ACCEPT || ticket.getStatus() == TicketStatus.IN_PROGRESS;
    }

    private boolean isHighPriority(RepairTicket ticket) {
        return "high".equalsIgnoreCase(ticket.getPriority());
    }

    private boolean isOverdueStaffTask(RepairTicket ticket, LocalDateTime now) {
        if (!isActiveStaffTask(ticket)) {
            return false;
        }
        return ticket.getCreatedAt() != null && ticket.getCreatedAt().isBefore(now.minusDays(2));
    }

    private boolean isTodayStaffTask(RepairTicket ticket, LocalDateTime start, LocalDateTime end) {
        return isWithinHalfOpenRange(ticket.getAssignedAt(), start, end)
            || isWithinHalfOpenRange(ticket.getEstimatedCompletionTime(), start, end)
            || wasReturnedToday(ticket, start, end);
    }

    private boolean wasReturnedToday(RepairTicket ticket, LocalDateTime start, LocalDateTime end) {
        return statusLogRepository.existsByTicketAndOldStatusAndNewStatusAndLogTimeGreaterThanEqualAndLogTimeLessThan(
            ticket,
            TicketStatus.RESOLVED,
            TicketStatus.WAITING_ACCEPT,
            start,
            end
        );
    }

    private boolean isWithinHalfOpenRange(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return value != null && !value.isBefore(start) && value.isBefore(end);
    }

    private Map<String, Object> studentTicketPageResult(Page<RepairTicket> ticketPage, int page, int size, String studentId) {
        Map<String, Object> result = staffTaskPageResult(ticketPage, page, size);
        result.put("stats", buildStudentTicketStats(studentId));
        return result;
    }

    private Map<String, Object> buildStudentTicketStats(String studentId) {
        List<RepairTicket> tickets = ticketRepository.findAll((Specification<RepairTicket>) (root, query, cb) -> cb.and(
            cb.equal(root.get("student").get("userId"), studentId),
            cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted")))
        ));
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", (long) tickets.size());
        stats.put("pending", countTicketsByStatus(tickets, TicketStatus.WAITING_ACCEPT));
        stats.put("processing", countTicketsByStatus(tickets, TicketStatus.IN_PROGRESS));
        stats.put("awaitingConfirmation", countTicketsByStatus(tickets, TicketStatus.RESOLVED));
        stats.put("toBeEvaluated", countTicketsByStatus(tickets, TicketStatus.WAITING_FEEDBACK));
        stats.put("completed", tickets.stream()
            .filter(ticket -> ticket.getStatus() == TicketStatus.FEEDBACKED || ticket.getStatus() == TicketStatus.CLOSED)
            .count());
        stats.put("rejected", countTicketsByStatus(tickets, TicketStatus.REJECTED));
        return stats;
    }

    private long countTicketsByStatus(List<RepairTicket> tickets, TicketStatus status) {
        return tickets.stream()
            .filter(ticket -> ticket.getStatus() == status)
            .count();
    }

    private Map<String, Object> staffTaskPageResult(Page<RepairTicket> ticketPage, int page, int size) {
        List<TicketSummaryDto> summaries = ticketPage.getContent().stream()
            .map(this::toSummaryDto)
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("list", summaries);
        result.put("total", ticketPage.getTotalElements());
        result.put("page", page);
        result.put("pageSize", size);
        result.put("totalPages", ticketPage.getTotalPages());
        return result;
    }

    @Transactional
    public TicketDetailDto updateRepairNotes(Long ticketId, String repairNotes, String operatorId) {
        RepairTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
        ticket.setRepairNotes(repairNotes);
        ticketRepository.save(ticket);
        auditEventPublisher.record(operatorId, "工单管理", "修改维修备注", "REPAIR_TICKET",
            String.valueOf(ticketId), "工单#" + ticketId + " 维修备注已更新");
        return toDetailDto(ticket);
    }

    @Transactional
    public TicketDetailDto updateProcessNotes(Long ticketId, String processNotes, String operatorId) {
        RepairTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
        ticket.setProcessNotes(processNotes);
        ticketRepository.save(ticket);
        auditEventPublisher.record(operatorId, "工单管理", "修改处理备注", "REPAIR_TICKET",
            String.valueOf(ticketId), "工单#" + ticketId + " 处理备注已更新");
        return toDetailDto(ticket);
    }

    @Transactional
    public TicketDetailDto setEstimatedCompletionTime(Long ticketId, LocalDateTime estimatedTime, String operatorId) {
        RepairTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
        ticket.setEstimatedCompletionTime(estimatedTime);
        ticketRepository.save(ticket);
        auditEventPublisher.record(operatorId, "工单管理", "修改预计完成时间", "REPAIR_TICKET",
            String.valueOf(ticketId), "工单#" + ticketId + " 预计完成时间已更新");
        return toDetailDto(ticket);
    }

    private boolean isValidStatusTransition(TicketStatus oldStatus, TicketStatus newStatus) {
        return switch (oldStatus) {
            case WAITING_ACCEPT -> newStatus == TicketStatus.IN_PROGRESS || newStatus == TicketStatus.REJECTED;
            case IN_PROGRESS -> newStatus == TicketStatus.RESOLVED || newStatus == TicketStatus.REJECTED;
            case RESOLVED -> false;
            case WAITING_FEEDBACK -> newStatus == TicketStatus.FEEDBACKED || newStatus == TicketStatus.CLOSED;
            case FEEDBACKED -> newStatus == TicketStatus.CLOSED;
            case REJECTED, CLOSED -> false;
        };
    }

    private void appendStatusLog(RepairTicket ticket, TicketStatus oldStatus, TicketStatus newStatus, UserReference operator) {
        TicketStatusLog log = new TicketStatusLog();
        log.setTicket(ticket);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setChangedBy(operator);
        log.setLogTime(LocalDateTime.now());
        statusLogRepository.save(log);
    }

    private void notifyAdminsNewTicket(RepairTicket ticket) {
        String title = "新报修工单待分配";
        String content = "工单 #" + ticket.getTicketId() + " 已提交，地点：" + safeText(ticket.getLocationText()) + "，请及时分配维修人员。";
        notificationService.notifyAdmins(title, content, ticket);
        // 实时推送
        List<UserReference> admins = userReferenceRepository.findByRoleAndIsActiveTrue(UserRole.ADMIN);
        notificationPushService.notifyAndPushBatch(admins, title, content, ticket);
    }

    private void notifyTicketAssigned(RepairTicket ticket, UserReference staff) {
        String staffTitle = "你有新的维修任务";
        String staffContent = "工单 #" + ticket.getTicketId() + " 已分配给你，地点：" + safeText(ticket.getLocationText()) + "。";
        notificationService.notifyUser(staff, staffTitle, staffContent, ticket);
        notificationPushService.notifyAndPush(staff, staffTitle, staffContent, ticket);

        String studentTitle = "报修工单已受理";
        String studentContent = "你的工单 #" + ticket.getTicketId() + " 已分配给维修人员：" + safeName(staff) + "。";
        notificationService.notifyUser(ticket.getStudent(), studentTitle, studentContent, ticket);
        notificationPushService.notifyAndPush(ticket.getStudent(), studentTitle, studentContent, ticket);
    }

    private void notifyStudentToConfirm(RepairTicket ticket) {
        String title = "Repair completed, please confirm";
        String content = "Ticket #" + ticket.getTicketId() + " has been marked repaired. Please confirm completion or return it with a reason.";
        notificationService.notifyUser(ticket.getStudent(), title, content, ticket);
        notificationPushService.notifyAndPush(ticket.getStudent(), title, content, ticket);
    }

    private void notifyStaffCompletionConfirmed(RepairTicket ticket) {
        String title = "Student confirmed completion";
        String content = "Ticket #" + ticket.getTicketId() + " has been confirmed by the student and is now waiting for feedback.";
        notificationService.notifyUser(ticket.getStaff(), title, content, ticket);
        notificationPushService.notifyAndPush(ticket.getStaff(), title, content, ticket);
    }

    private void notifyStaffCompletionRejected(RepairTicket ticket, String reason) {
        String title = "Student returned the repair task";
        String content = "Ticket #" + ticket.getTicketId() + " was returned to processing. Reason: " + safeText(reason);
        notificationService.notifyUser(ticket.getStaff(), title, content, ticket);
        notificationPushService.notifyAndPush(ticket.getStaff(), title, content, ticket);
    }

    private void assertStudentOwnsTicket(RepairTicket ticket, String studentId) {
        if (ticket.getStudent() == null || !Objects.equals(ticket.getStudent().getUserId(), studentId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only the ticket owner can perform this action");
        }
    }

    private StaffRecommendationDto buildStaffRecommendation(UserReference staff, String targetCategory, String targetLocation) {
        String staffId = staff.getUserId();
        int activeTaskCount = ticketRepository.countActiveTasksByStaffId(staffId).intValue();
        int sameCategoryCompletedCount = targetCategory == null ? 0 :
            ticketRepository.countCompletedTasksByStaffIdAndCategory(staffId, targetCategory).intValue();
        int completedTaskCount = ticketRepository.countCompletedTasksByStaffId(staffId).intValue();
        Double averageRatingRaw = ticketRepository.findAverageRatingByStaffId(staffId);
        double averageRating = averageRatingRaw != null ? averageRatingRaw : 0.0;
        Double averageProcessingHoursRaw = ticketRepository.findAverageProcessingHoursByStaffId(staffId);
        double averageProcessingHours = averageProcessingHoursRaw != null ? averageProcessingHoursRaw : 72.0;

        double loadScore = Math.max(0, 1.0 - Math.min(activeTaskCount, 8) / 8.0) * 35.0;
        double categoryScore = Math.min(sameCategoryCompletedCount, 5) / 5.0 * 25.0;
        double ratingScore = averageRating > 0 ? averageRating / 5.0 * 20.0 : 12.0;
        double speedScore = Math.max(0, 1.0 - Math.min(averageProcessingHours, 120.0) / 120.0) * 20.0;
        boolean areaMatched = matchesProfileText(staff.getResponsibleArea(), targetLocation);
        boolean specialtyMatched = matchesProfileText(staff.getSpecialties(), targetCategory);
        double matchBonus = (areaMatched ? 20.0 : 0.0) + (specialtyMatched ? 20.0 : 0.0);
        double score = Math.round((loadScore + categoryScore + ratingScore + speedScore + matchBonus) * 10.0) / 10.0;

        String reason = "当前待办 " + activeTaskCount + " 单，同类完成 " + sameCategoryCompletedCount + " 单，平均评分 " +
            String.format(Locale.ROOT, "%.1f", averageRating) + "，平均处理 " +
            String.format(Locale.ROOT, "%.1f", averageProcessingHours) + " 小时";
        List<String> matchReasons = new ArrayList<>();
        if (areaMatched) {
            matchReasons.add("区域匹配");
        }
        if (specialtyMatched) {
            matchReasons.add("专业匹配");
        }
        if (!matchReasons.isEmpty()) {
            reason = String.join("、", matchReasons) + "；" + reason;
        } else if (!hasText(staff.getResponsibleArea()) && !hasText(staff.getSpecialties())) {
            reason = "未维护负责区域/专业特长，按原有负载和服务质量推荐；" + reason;
        }

        return new StaffRecommendationDto(staff.getUserId(), safeName(staff), staff.getContactPhone(),
            score, activeTaskCount, sameCategoryCompletedCount, completedTaskCount,
            Math.round(averageRating * 10.0) / 10.0, Math.round(averageProcessingHours * 10.0) / 10.0,
            staff.getResponsibleArea(), staff.getSpecialties(), areaMatched, specialtyMatched, reason);
    }

    private boolean matchesProfileText(String profileText, String targetText) {
        if (!hasText(profileText) || !hasText(targetText)) {
            return false;
        }
        String normalizedTarget = normalizeMatchText(targetText);
        for (String token : profileText.split("[,，;；、\\s]+")) {
            String normalizedToken = normalizeMatchText(token);
            if (normalizedToken.length() >= 2
                && (normalizedTarget.contains(normalizedToken) || normalizedToken.contains(normalizedTarget))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeMatchText(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private TicketDetailDto toDetailDto(RepairTicket ticket) {
        return toDetailDto(ticket, false);
    }

    private TicketDetailDto toDetailDto(RepairTicket ticket, boolean includeAdminAiFields) {
        List<TicketImageDto> images = imageRepository.findByTicket(ticket).stream()
            .map(image -> new TicketImageDto(image.getImageId(), image.getImageUrl(), image.getUploadedAt()))
            .collect(Collectors.toList());

        List<TicketStatusLogDto> logs = statusLogRepository.findByTicketOrderByLogTimeAsc(ticket).stream()
            .map(log -> new TicketStatusLogDto(log.getLogId(), log.getOldStatus(), log.getNewStatus(),
                log.getChangedBy() != null ? log.getChangedBy().getUserId() : null, log.getLogTime()))
            .collect(Collectors.toList());

        RatingDto ratingDto = ratingRepository.findByTicket(ticket)
            .map(ratingDtoMapper::toDto)
            .orElse(null);

        return new TicketDetailDto(ticket.getTicketId(), ticket.getStatus(),
            ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : null,
            ticket.getStudent() != null ? ticket.getStudent().getUserId() : null,
            ticket.getStudent() != null ? ticket.getStudent().getNickname() : null,
            ticket.getStaff() != null ? ticket.getStaff().getUserId() : null,
            ticket.getStaff() != null ? ticket.getStaff().getNickname() : null,
            ticket.getLocationText(), ticket.getDescription(), ticket.getRejectionReason(),
            ticket.getPriority(), ticket.getRepairNotes(), ticket.getProcessNotes(),
            ticket.getEstimatedCompletionTime(), ticket.getCreatedAt(), ticket.getAssignedAt(),
            ticket.getCompletedAt(), ticket.getStudentConfirmedAt(), ticket.getStudentRejectionReason(),
            ticket.getClosedAt(), images, logs, ratingDto,
            ticketCompletionSummaryService.getByTicketId(ticket.getTicketId()),
            aiAnalysisIntegrationService.getViewByTicketId(ticket.getTicketId(), includeAdminAiFields),
            historicalRepairCaseService.recommendForTicket(ticket.getTicketId(), 5));
    }

    private void triggerCompletionSummaryIfNeeded(RepairTicket ticket) {
        if (!isCompletionSummaryStatus(ticket.getStatus())) {
            return;
        }
        Long ticketId = ticket.getTicketId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ticketCompletionSummaryService.generateAsync(ticketId);
                }
            });
        } else {
            ticketCompletionSummaryService.generateAsync(ticketId);
        }
    }

    private void triggerHistoricalRepairCaseSync(Long ticketId) {
        if (ticketId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    historicalRepairCaseService.syncTicketAsync(ticketId);
                }
            });
        } else {
            historicalRepairCaseService.syncTicketAsync(ticketId);
        }
    }

    private void triggerHistoricalRepairCaseDelete(Long ticketId) {
        if (ticketId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    historicalRepairCaseService.deleteTicketAsync(ticketId);
                }
            });
        } else {
            historicalRepairCaseService.deleteTicketAsync(ticketId);
        }
    }

    private void triggerFeedbackSentimentIfNeeded(Long ratingId) {
        if (ratingId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    feedbackSentimentAnalysisService.analyzeAsync(ratingId);
                }
            });
        } else {
            feedbackSentimentAnalysisService.analyzeAsync(ratingId);
        }
    }

    private boolean isCompletionSummaryStatus(TicketStatus status) {
        return status == TicketStatus.RESOLVED
            || status == TicketStatus.WAITING_FEEDBACK
            || status == TicketStatus.CLOSED
            || status == TicketStatus.FEEDBACKED;
    }

    private TicketSummaryDto toSummaryDto(RepairTicket ticket) {
        Integer ratingScore = ratingRepository.findByTicket(ticket).map(Rating::getScore).orElse(null);
        return new TicketSummaryDto(ticket.getTicketId(), ticket.getStatus(),
            ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : null,
            ticket.getStudent() != null ? ticket.getStudent().getUserId() : null,
            ticket.getStaff() != null ? ticket.getStaff().getUserId() : null,
            ticket.getLocationText(), ticket.getDescription(), ticket.getPriority(),
            ticket.getCreatedAt(), ticket.getAssignedAt(), ticket.getEstimatedCompletionTime(),
            ratingScore, ticket.getDeleted(), ticket.getDeletedAt());
    }

    private String safeName(UserReference user) {
        if (user == null) return "未指定";
        return user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getUserId();
    }

    private String safeText(String text) {
        return text == null || text.isBlank() ? "未填写" : text;
    }

    // ==================== 统计方法 ====================

    @Transactional(readOnly = true)
    public List<LocationStatsDto> getLocationStats() {
        List<Object[]> results = ticketRepository.findLocationStats();
        return results.stream()
            .map(row -> new LocationStatsDto(
                (String) row[0],
                ((Long) row[1]).intValue()
            ))
            .limit(10)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RepairmanRatingStatsDto> getRepairmanRatingStats() {
        List<Object[]> results = ticketRepository.findRepairmanRatingStats();
        return results.stream()
            .map(row -> {
                String id = (String) row[0];
                String name = (String) row[1];
                Number avgRatingNum = (Number) row[2];
                Number completedNum = (Number) row[3];
                int rating = avgRatingNum != null ? (int) Math.round(avgRatingNum.doubleValue()) : 0;
                int completed = completedNum != null ? completedNum.intValue() : 0;
                return new RepairmanRatingStatsDto(id, name, rating, completed);
            })
            .limit(10)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCategoryStatsFromView() {
        List<Object[]> results = ticketRepository.findCategoryStatsFromView();
        List<Map<String, Object>> stats = new ArrayList<>();

        for (Object[] row : results) {
            String categoryKey = (String) row[0];
            String categoryName = (String) row[1];
            Number totalTicketsNum = (Number) row[2];
            Number completedTicketsNum = (Number) row[3];
            Number ratedTicketsNum = (Number) row[4];
            Number avgRatingNum = (Number) row[5];

            Map<String, Object> item = new HashMap<>();
            item.put("category", categoryName);
            item.put("name", categoryName);
            item.put("type", categoryName);
            int total = totalTicketsNum != null ? totalTicketsNum.intValue() : 0;
            item.put("count", total);
            item.put("value", total);

            item.put("categoryKey", categoryKey);
            item.put("totalTickets", total);
            item.put("completedTickets", completedTicketsNum != null ? completedTicketsNum.intValue() : 0);
            item.put("ratedTickets", ratedTicketsNum != null ? ratedTicketsNum.intValue() : 0);
            item.put("avgRating", avgRatingNum != null ? avgRatingNum.doubleValue() : 0.0);

            stats.add(item);
        }

        return stats;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMonthlyStats() {
        java.time.LocalDate now = java.time.LocalDate.now();
        Map<String, Object> monthlyStats = new HashMap<>();

        List<Map<String, Object>> monthsData = new ArrayList<>();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");

        for (int i = 11; i >= 0; i--) {
            java.time.LocalDate month = now.minusMonths(i);
            String monthStr = month.format(formatter);

            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", monthStr);
            monthData.put("count", ticketRepository.countByMonth(monthStr));
            monthsData.add(monthData);
        }

        monthlyStats.put("months", monthsData);

        Map<String, Long> statusStats = new HashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            statusStats.put(status.name(), ticketRepository.countByStatus(status));
        }
        monthlyStats.put("statusDistribution", statusStats);

        return monthlyStats;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAverageProcessingTime() {
        Double avgHours = ticketRepository.findAverageProcessingTimeHours();
        Long completedCount = ticketRepository.countCompletedTickets();

        Map<String, Object> result = new HashMap<>();
        result.put("completedTickets", completedCount);

        if (avgHours != null && avgHours > 0 && completedCount > 0) {
            double totalHours = avgHours;

            if (totalHours >= 24) {
                double days = totalHours / 24;
                result.put("avgHours", Math.round(totalHours));
                result.put("avgDays", Math.round(days * 10) / 10.0);
                result.put("displayText", String.format("约 %.1f 天", days));
            } else {
                result.put("avgHours", Math.round(totalHours));
                result.put("avgDays", 0);
                result.put("displayText", String.format("约 %.0f 小时", totalHours));
            }
        } else {
            result.put("avgHours", 0);
            result.put("avgDays", 0);
            result.put("displayText", "暂无数据");
        }

        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSlaOverview() {
        LocalDateTime now = LocalDateTime.now();
        List<RepairTicket> tickets = ticketRepository.findAll();
        List<Map<String, Object>> alertTickets = new ArrayList<>();

        int activeCount = 0;
        int overdueAcceptCount = 0;
        int overdueCompletionCount = 0;
        int warningCount = 0;

        for (RepairTicket ticket : tickets) {
            if (Boolean.TRUE.equals(ticket.getDeleted())) {
                continue;
            }
            TicketStatus status = ticket.getStatus();
            if (status != TicketStatus.WAITING_ACCEPT && status != TicketStatus.IN_PROGRESS) {
                continue;
            }

            activeCount++;
            Map<String, Object> slaItem = buildSlaItem(ticket, now);
            if (slaItem == null) {
                continue;
            }

            String slaStatus = String.valueOf(slaItem.get("slaStatus"));
            String slaType = String.valueOf(slaItem.get("slaType"));
            if ("OVERDUE".equals(slaStatus) && "ACCEPTANCE".equals(slaType)) {
                overdueAcceptCount++;
            } else if ("OVERDUE".equals(slaStatus) && "COMPLETION".equals(slaType)) {
                overdueCompletionCount++;
            } else if ("WARNING".equals(slaStatus)) {
                warningCount++;
            }

            alertTickets.add(slaItem);
        }

        alertTickets.sort((left, right) -> {
            int leftRank = "OVERDUE".equals(left.get("slaStatus")) ? 0 : 1;
            int rightRank = "OVERDUE".equals(right.get("slaStatus")) ? 0 : 1;
            if (leftRank != rightRank) {
                return Integer.compare(leftRank, rightRank);
            }
            LocalDateTime leftDue = (LocalDateTime) left.get("dueAt");
            LocalDateTime rightDue = (LocalDateTime) right.get("dueAt");
            if (leftDue == null && rightDue == null) {
                return 0;
            }
            if (leftDue == null) {
                return 1;
            }
            if (rightDue == null) {
                return -1;
            }
            return leftDue.compareTo(rightDue);
        });

        int alertTotal = overdueAcceptCount + overdueCompletionCount + warningCount;
        Map<String, Object> result = new HashMap<>();
        result.put("activeCount", activeCount);
        result.put("uniqueActiveCount", activeCount);
        result.put("overdueAcceptCount", overdueAcceptCount);
        result.put("overdueCompletionCount", overdueCompletionCount);
        result.put("warningCount", warningCount);
        result.put("alertTotal", alertTotal);
        result.put("overdueRate", activeCount == 0 ? 0.0 : Math.round((overdueAcceptCount + overdueCompletionCount) * 1000.0 / activeCount) / 10.0);
        result.put("alertTickets", alertTickets);
        result.put("rules", buildSlaRules());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getHotspotAnalysis() {
        Map<String, Object> result = new HashMap<>();

        try {
            result.put("hotAreas", mapHotAreas(ticketRepository.findHotAreaStats()));
        } catch (Exception e) {
            result.put("hotAreas", new ArrayList<>());
        }

        try {
            result.put("categoryGrowth", mapCategoryGrowth(ticketRepository.findCategoryGrowthStats()));
        } catch (Exception e) {
            result.put("categoryGrowth", new ArrayList<>());
        }

        try {
            result.put("repeatedLocations", mapRepeatedLocations(ticketRepository.findRepeatedLocationStats()));
        } catch (Exception e) {
            result.put("repeatedLocations", new ArrayList<>());
        }

        try {
            result.put("staffWorkload", mapStaffWorkload(ticketRepository.findStaffWorkloadStats()));
        } catch (Exception e) {
            result.put("staffWorkload", new ArrayList<>());
        }

        try {
            result.put("categoryProcessingTime", mapCategoryProcessingTime(ticketRepository.findCategoryProcessingTimeStats()));
        } catch (Exception e) {
            result.put("categoryProcessingTime", new ArrayList<>());
        }

        result.put("generatedAt", LocalDateTime.now());
        return result;
    }

    private Map<String, Object> buildSlaItem(RepairTicket ticket, LocalDateTime now) {
        String priority = normalizePriority(ticket.getPriority());
        RepairRuleConfigService.SlaRules rules = repairRuleConfigService.slaRules();
        RepairRuleConfigService.SlaPriorityRule priorityRule = rules.priority(priority);
        long responseHours = priorityRule.responseHours();
        long completionHours = priorityRule.completionHours();

        LocalDateTime startAt;
        LocalDateTime dueAt;
        String slaType;
        String slaLabel;
        long limitHours;

        if (ticket.getStatus() == TicketStatus.WAITING_ACCEPT) {
            startAt = ticket.getCreatedAt();
            dueAt = startAt != null ? startAt.plusHours(responseHours) : null;
            slaType = "ACCEPTANCE";
            slaLabel = "受理时限";
            limitHours = responseHours;
        } else if (ticket.getStatus() == TicketStatus.IN_PROGRESS) {
            startAt = ticket.getAssignedAt() != null ? ticket.getAssignedAt() : ticket.getCreatedAt();
            dueAt = startAt != null ? startAt.plusHours(completionHours) : null;
            slaType = "COMPLETION";
            slaLabel = "完成时限";
            limitHours = completionHours;
        } else {
            return null;
        }

        if (dueAt == null) {
            return null;
        }

        boolean overdue = now.isAfter(dueAt);
        long warningThreshold = Math.max(1, Math.round(limitHours * rules.warningRatio()));
        boolean warning = !overdue && !now.isBefore(dueAt.minusHours(warningThreshold));
        if (!overdue && !warning) {
            return null;
        }

        long remainingHours = java.time.Duration.between(now, dueAt).toHours();
        long overdueHours = overdue ? java.time.Duration.between(dueAt, now).toHours() : 0;

        Map<String, Object> item = new HashMap<>();
        item.put("ticketId", ticket.getTicketId());
        item.put("status", ticket.getStatus());
        item.put("categoryName", ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : null);
        item.put("studentId", ticket.getStudent() != null ? ticket.getStudent().getUserId() : null);
        item.put("staffId", ticket.getStaff() != null ? ticket.getStaff().getUserId() : null);
        item.put("locationText", ticket.getLocationText());
        item.put("description", ticket.getDescription());
        item.put("priority", ticket.getPriority());
        item.put("createdAt", ticket.getCreatedAt());
        item.put("assignedAt", ticket.getAssignedAt());
        item.put("estimatedCompletionTime", ticket.getEstimatedCompletionTime());
        item.put("deleted", ticket.getDeleted());
        item.put("deletedAt", ticket.getDeletedAt());
        item.put("slaType", slaType);
        item.put("slaStatus", overdue ? "OVERDUE" : "WARNING");
        item.put("slaLabel", slaLabel);
        item.put("dueAt", dueAt);
        item.put("remainingHours", Math.max(0, remainingHours));
        item.put("overdueHours", Math.max(0, overdueHours));
        item.put("limitHours", limitHours);
        return item;
    }

    private List<Map<String, Object>> buildSlaRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        RepairRuleConfigService.SlaRules config = repairRuleConfigService.slaRules();
        rules.add(buildSlaRule("high", config.priority("high").responseHours(), config.priority("high").completionHours()));
        rules.add(buildSlaRule("medium", config.priority("medium").responseHours(), config.priority("medium").completionHours()));
        rules.add(buildSlaRule("low", config.priority("low").responseHours(), config.priority("low").completionHours()));
        return rules;
    }

    private Map<String, Object> buildSlaRule(String priority, long responseHours, long completionHours) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("priority", priority);
        rule.put("responseHours", responseHours);
        rule.put("completionHours", completionHours);
        return rule;
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "medium";
        }
        String normalized = priority.toLowerCase(java.util.Locale.ROOT);
        if ("high".equals(normalized) || "medium".equals(normalized) || "low".equals(normalized)) {
            return normalized;
        }
        return "medium";
    }

    private List<Map<String, Object>> mapHotAreas(List<Object[]> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("area", row[0] != null ? row[0].toString() : "未知区域");
            item.put("totalTickets", toInt((Number) row[1]));
            item.put("activeTickets", toInt((Number) row[2]));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> mapCategoryGrowth(List<Object[]> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            int recent = toInt((Number) row[1]);
            int previous = toInt((Number) row[2]);
            Map<String, Object> item = new HashMap<>();
            item.put("category", row[0] != null ? row[0].toString() : "未分类");
            item.put("recentCount", recent);
            item.put("previousCount", previous);
            item.put("growth", recent - previous);
            item.put("growthRate", previous == 0 ? (recent > 0 ? 100.0 : 0.0) : Math.round((recent - previous) * 1000.0 / previous) / 10.0);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> mapRepeatedLocations(List<Object[]> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("location", row[0] != null ? row[0].toString() : "未知位置");
            item.put("category", row[1] != null ? row[1].toString() : "未分类");
            item.put("totalTickets", toInt((Number) row[2]));
            item.put("activeTickets", toInt((Number) row[3]));
            item.put("lastCreatedAt", row[4]);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> mapStaffWorkload(List<Object[]> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("staffId", row[0] != null ? row[0].toString() : "");
            item.put("staffName", row[1] != null ? row[1].toString() : "未知维修员");
            item.put("totalAssigned", toInt((Number) row[2]));
            item.put("activeTickets", toInt((Number) row[3]));
            item.put("completedTickets", toInt((Number) row[4]));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> mapCategoryProcessingTime(List<Object[]> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            double avgHours = row[2] == null ? 0.0 : ((Number) row[2]).doubleValue();
            Map<String, Object> item = new HashMap<>();
            item.put("category", row[0] != null ? row[0].toString() : "未分类");
            item.put("completedTickets", toInt((Number) row[1]));
            item.put("avgHours", Math.round(avgHours * 10.0) / 10.0);
            item.put("displayText", formatHoursDisplay(avgHours));
            result.add(item);
        }
        return result;
    }

    private String formatHoursDisplay(double hours) {
        if (hours <= 0) {
            return "暂无数据";
        }
        if (hours >= 24) {
            return String.format(java.util.Locale.ROOT, "%.1f 天", hours / 24.0);
        }
        return String.format(java.util.Locale.ROOT, "%.1f 小时", hours);
    }

    private int toInt(Number num) {
        return num != null ? num.intValue() : 0;
    }
}
