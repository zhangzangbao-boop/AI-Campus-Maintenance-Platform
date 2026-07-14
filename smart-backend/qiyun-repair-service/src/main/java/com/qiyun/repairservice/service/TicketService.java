package com.qiyun.repairservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.feign.client.AiServiceClient;
import com.qiyun.feign.dto.AnalyzeTicketRequest;
import com.qiyun.repairservice.domain.entity.Category;
import com.qiyun.repairservice.domain.entity.Rating;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.TicketImage;
import com.qiyun.repairservice.domain.entity.TicketStatusLog;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.dto.*;
import com.qiyun.repairservice.dto.request.TicketAssignRequest;
import com.qiyun.repairservice.dto.request.TicketCreateRequest;
import com.qiyun.repairservice.dto.request.TicketRatingRequest;
import com.qiyun.repairservice.dto.request.TicketStatusUpdateRequest;
import com.qiyun.repairservice.repository.*;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketImageRepository imageRepository;
    private final TicketStatusLogRepository statusLogRepository;
    private final RatingRepository ratingRepository;
    private final UserReferenceRepository userReferenceRepository;
    private final CategoryService categoryService;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final AiServiceClient aiServiceClient;

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
            aiServiceClient.analyzeTicket(new AnalyzeTicketRequest(ticket.getDescription(), ticket.getLocationText()));
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
            if (status != TicketStatus.WAITING_FEEDBACK && status != TicketStatus.RESOLVED && status != TicketStatus.CLOSED) {
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
            ratingRepository.save(rating);

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
    }

    @Transactional
    public TicketDetailDto getTicketDetail(Long ticketId) {
        RepairTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
        return toDetailDto(ticket);
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
    public List<StaffRecommendationDto> recommendStaffForTicket(Long ticketId) {
        RepairTicket targetTicket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
        String targetCategory = targetTicket.getCategory() != null ? targetTicket.getCategory().getCategoryName() : null;

        List<UserReference> staffUsers = userReferenceRepository.findByRoleAndIsActiveTrue(UserRole.STAFF);
        return staffUsers.stream()
            .map(staff -> buildStaffRecommendation(staff, targetCategory))
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
        if (ticket.getStaff() != null) {
            throw new BusinessException("该工单已被分配");
        }

        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStaff(staff);
        ticket.setAssignedAt(LocalDateTime.now());
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
        appendStatusLog(ticket, oldStatus, TicketStatus.RESOLVED, staff);
        return toDetailDto(ticket);
    }

    @Transactional(readOnly = true)
    public StaffDashboardDto getStaffDashboard(String staffId) {
        Long pendingCount = ticketRepository.countByStaffIdAndStatus(staffId, "WAITING_ACCEPT");
        Long inProgressCount = ticketRepository.countByStaffIdAndStatus(staffId, "IN_PROGRESS");
        Long completedCount = ticketRepository.countCompletedTasksByStaffId(staffId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        Long todayCompleted = ticketRepository.countCompletedByStaffIdAndCompletedAtBetween(staffId, todayStart, todayEnd);

        LocalDateTime weekStart = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusWeeks(1);
        Long weekCompleted = ticketRepository.countCompletedByStaffIdAndCompletedAtBetween(staffId, weekStart, weekEnd);

        LocalDateTime monthStart = now.toLocalDate().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        LocalDateTime monthEnd = now.toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth()).atStartOfDay();
        Long monthCompleted = ticketRepository.countCompletedByStaffIdAndCompletedAtBetween(staffId, monthStart, monthEnd);

        Double avgRating = ticketRepository.findAverageRatingByStaffId(staffId);
        if (avgRating == null) avgRating = 0.0;

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
            pendingCount != null ? pendingCount : 0L,
            inProgressCount != null ? inProgressCount : 0L,
            completedCount != null ? completedCount : 0L,
            todayCompleted != null ? todayCompleted : 0L,
            weekCompleted != null ? weekCompleted : 0L,
            monthCompleted != null ? monthCompleted : 0L,
            avgRating,
            0L,
            priorityDistribution,
            categoryDistribution,
            avgProcessingHours
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listStaffTasksPage(String staffId, TicketStatus status, int page, int size) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        Page<RepairTicket> ticketPage = ticketRepository.findByStaffIdWithFilter(staffId, status, pageable);

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
        return toDetailDto(ticket);
    }

    @Transactional
    public TicketDetailDto updateProcessNotes(Long ticketId, String processNotes, String operatorId) {
        RepairTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
        ticket.setProcessNotes(processNotes);
        ticketRepository.save(ticket);
        return toDetailDto(ticket);
    }

    @Transactional
    public TicketDetailDto setEstimatedCompletionTime(Long ticketId, LocalDateTime estimatedTime, String operatorId) {
        RepairTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
        ticket.setEstimatedCompletionTime(estimatedTime);
        ticketRepository.save(ticket);
        return toDetailDto(ticket);
    }

    private boolean isValidStatusTransition(TicketStatus oldStatus, TicketStatus newStatus) {
        return switch (oldStatus) {
            case WAITING_ACCEPT -> newStatus == TicketStatus.IN_PROGRESS || newStatus == TicketStatus.REJECTED;
            case IN_PROGRESS -> newStatus == TicketStatus.RESOLVED || newStatus == TicketStatus.REJECTED;
            case RESOLVED -> newStatus == TicketStatus.WAITING_FEEDBACK || newStatus == TicketStatus.CLOSED;
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
        notificationService.notifyAdmins("新报修工单待分配",
            "工单 #" + ticket.getTicketId() + " 已提交，地点：" + safeText(ticket.getLocationText()) + "，请及时分配维修人员。", ticket);
    }

    private void notifyTicketAssigned(RepairTicket ticket, UserReference staff) {
        notificationService.notifyUser(staff, "你有新的维修任务",
            "工单 #" + ticket.getTicketId() + " 已分配给你，地点：" + safeText(ticket.getLocationText()) + "。", ticket);
        notificationService.notifyUser(ticket.getStudent(), "报修工单已受理",
            "你的工单 #" + ticket.getTicketId() + " 已分配给维修人员：" + safeName(staff) + "。", ticket);
    }

    private StaffRecommendationDto buildStaffRecommendation(UserReference staff, String targetCategory) {
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
        double score = Math.round((loadScore + categoryScore + ratingScore + speedScore) * 10.0) / 10.0;

        String reason = "当前待办 " + activeTaskCount + " 单，同类完成 " + sameCategoryCompletedCount + " 单，平均评分 " +
            String.format(Locale.ROOT, "%.1f", averageRating) + "，平均处理 " +
            String.format(Locale.ROOT, "%.1f", averageProcessingHours) + " 小时";

        return new StaffRecommendationDto(staff.getUserId(), safeName(staff), staff.getContactPhone(),
            score, activeTaskCount, sameCategoryCompletedCount, completedTaskCount,
            Math.round(averageRating * 10.0) / 10.0, Math.round(averageProcessingHours * 10.0) / 10.0, reason);
    }

    private TicketDetailDto toDetailDto(RepairTicket ticket) {
        List<TicketImageDto> images = imageRepository.findByTicket(ticket).stream()
            .map(image -> new TicketImageDto(image.getImageId(), image.getImageUrl(), image.getUploadedAt()))
            .collect(Collectors.toList());

        List<TicketStatusLogDto> logs = statusLogRepository.findByTicketOrderByLogTimeAsc(ticket).stream()
            .map(log -> new TicketStatusLogDto(log.getLogId(), log.getOldStatus(), log.getNewStatus(),
                log.getChangedBy() != null ? log.getChangedBy().getUserId() : null, log.getLogTime()))
            .collect(Collectors.toList());

        RatingDto ratingDto = ratingRepository.findByTicket(ticket)
            .map(rating -> new RatingDto(rating.getRatingId(), rating.getScore(), rating.getComment(),
                rating.getStudent() != null ? rating.getStudent().getUserId() : null,
                rating.getStudent() != null ? rating.getStudent().getNickname() : null,
                rating.getStaff() != null ? rating.getStaff().getUserId() : null,
                rating.getStaff() != null ? rating.getStaff().getNickname() : null,
                ticket.getTicketId(), rating.getSpeedRating(), rating.getQualityRating(),
                rating.getAttitudeRating(), rating.getResolved(), rating.getAnonymous(), rating.getRatedAt()))
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
            ticket.getCompletedAt(), ticket.getClosedAt(), images, logs, ratingDto);
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
}