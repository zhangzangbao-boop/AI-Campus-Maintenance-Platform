package com.ligong.reportingcenter.service;

import com.ligong.reportingcenter.domain.entity.Category;
import com.ligong.reportingcenter.domain.entity.Rating;
import com.ligong.reportingcenter.domain.entity.RepairTicket;
import com.ligong.reportingcenter.domain.entity.TicketImage;
import com.ligong.reportingcenter.domain.entity.TicketStatusLog;
import com.ligong.reportingcenter.domain.entity.User;
import com.ligong.reportingcenter.domain.enums.TicketStatus;
import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.*;
import com.ligong.reportingcenter.dto.request.TicketAssignRequest;
import com.ligong.reportingcenter.dto.request.TicketCreateRequest;
import com.ligong.reportingcenter.dto.request.TicketImageRequest;
import com.ligong.reportingcenter.dto.request.TicketRatingRequest;
import com.ligong.reportingcenter.dto.request.TicketStatusUpdateRequest;
import com.qiyun.common.exception.BusinessException;
import com.ligong.reportingcenter.repository.RatingRepository;
import com.ligong.reportingcenter.repository.TicketImageRepository;
import com.ligong.reportingcenter.repository.TicketRepository;
import com.ligong.reportingcenter.repository.TicketStatusLogRepository;
import com.ligong.reportingcenter.repository.UserRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketImageRepository imageRepository;
    private final TicketStatusLogRepository statusLogRepository;
    private final RatingRepository ratingRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final SystemConfigService systemConfigService;
    private final AiAnalysisIntegrationService aiAnalysisIntegrationService;

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    @Transactional
    public TicketDetailDto createTicket(TicketCreateRequest request) {
        User student = userService.loadActiveUser(request.studentId());
        if (student.getRole() != UserRole.STUDENT) {
            throw new BusinessException("仅学生账号可以提交报修单");
        }

        // 验证分类是否存在
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
        ticket.setCreatedAt(LocalDateTime.now());  // 明确设置
        ticket.setPriority(request.priority());

        // 关键修改：确保保存后获取到含有ID的实体
        ticket = ticketRepository.save(ticket);

        // 强制刷新确保数据写入数据库
        ticketRepository.flush();

        appendStatusLog(ticket, null, TicketStatus.WAITING_ACCEPT, student);

        if (request.imageUrls() != null) {
            for (String url : request.imageUrls()) {
                TicketImage image = new TicketImage();
                image.setTicket(ticket);
                image.setImageUrl(url);
                image.setUploadedAt(LocalDateTime.now()); // 明确设置时间
                imageRepository.save(image);
            }
        }
        ticketRepository.flush();

        // 异步调用 AI 分析（不阻塞主流程）
        try {
            aiAnalysisIntegrationService.analyzeAndSave(
                ticket.getTicketId(),
                ticket.getDescription(),
                ticket.getLocationText()
            );
        } catch (Exception e) {
            // AI 分析失败不影响工单创建
            log.warn("AI 分析调用失败: ticketId={}, error={}", ticket.getTicketId(), e.getMessage());
        }

        notifyAdminsNewTicket(ticket);
        return toDetailDto(ticket);
    }

    @Transactional
    public TicketDetailDto createTicket(TicketCreateRequest request, List<MultipartFile> images) {
        User student = userService.loadActiveUser(request.studentId());
        if (student.getRole() != UserRole.STUDENT) {
            throw new BusinessException("仅学生账号可以提交报修单");
        }

        // 验证分类是否存在
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
        ticket.setCreatedAt(LocalDateTime.now());  // 明确设置
        ticket.setPriority(request.priority());

        // 关键修改：确保保存后获取到含有ID的实体
        ticket = ticketRepository.save(ticket);

        // 强制刷新确保数据写入数据库
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

        // 异步调用 AI 分析（不阻塞主流程）
        try {
            aiAnalysisIntegrationService.analyzeAndSave(
                ticket.getTicketId(),
                ticket.getDescription(),
                ticket.getLocationText()
            );
        } catch (Exception e) {
            // AI 分析失败不影响工单创建
            log.warn("AI 分析调用失败: ticketId={}, error={}", ticket.getTicketId(), e.getMessage());
        }

        notifyAdminsNewTicket(ticket);
        return toDetailDto(ticket);
    }

    @Transactional
    public TicketDetailDto createTicketFromFormData(
            String studentId,
            Long categoryId,
            String locationText,
            String description,
            String priority,
            List<MultipartFile> images) {
        // 构造请求对象
        TicketCreateRequest request = new TicketCreateRequest(
            studentId,
            categoryId,
            locationText,
            description,
            priority,
            Collections.emptyList() // 图片URL将在后续处理
        );
        
        return createTicket(request, images);
    }

    @Transactional
    public TicketDetailDto assignTicket(Long ticketId, TicketAssignRequest request) {
        try {
            // 使用悲观锁防止并发分配
            RepairTicket ticket = findTicketWithLock(ticketId);
            User operator = userService.loadActiveUser(request.operatorId());
            User staff = userService.loadActiveUser(request.staffId());
            if (staff.getRole() != UserRole.STAFF) {
                throw new BusinessException("仅维修工角色可以被分配");
            }
            if (ticket.getStatus() != TicketStatus.WAITING_ACCEPT
                    && ticket.getStatus() != TicketStatus.IN_PROGRESS) {
                throw new BusinessException("当前工单状态不允许派单或转派");
            }
            User oldStaff = ticket.getStaff();
            ticket.setStaff(staff);
            ticket.setAssignedAt(LocalDateTime.now());
            TicketStatus oldStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            appendStatusLog(ticket, oldStatus, TicketStatus.IN_PROGRESS, operator);
            if (oldStaff != null && !oldStaff.getUserId().equals(staff.getUserId())) {
                notificationService.notifyUser(
                    oldStaff,
                    "工单已转派",
                    "工单 #" + ticket.getTicketId() + " 已转派给 " + safeName(staff) + " 继续处理。",
                    ticket
                );
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
            // 使用悲观锁防止并发状态更新
            RepairTicket ticket = findTicketWithLock(ticketId);
            User operator = userService.loadActiveUser(request.operatorId());
            TicketStatus oldStatus = ticket.getStatus();
            TicketStatus newStatus = request.newStatus();
            User oldStaff = ticket.getStaff();

            // 新增状态转换合法性校验
            if (!isValidStatusTransition(oldStatus, newStatus)) {
                throw new BusinessException("不允许从 " + oldStatus + " 转换到 " + newStatus);
            }

            // 状态变更逻辑（补充完整字段映射）
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
            notifyStatusChanged(ticket, oldStatus, newStatus, oldStaff);
            return toDetailDto(ticket);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("工单已被其他用户修改，请刷新后重试");
        }
    }

    // 新增状态转换规则校验方法
    private boolean isValidStatusTransition(TicketStatus oldStatus, TicketStatus newStatus) {
        return switch (oldStatus) {
            case WAITING_ACCEPT ->
                    newStatus == TicketStatus.IN_PROGRESS || newStatus == TicketStatus.REJECTED;
            case IN_PROGRESS ->
                    newStatus == TicketStatus.RESOLVED || newStatus == TicketStatus.REJECTED;
            case RESOLVED ->
                    false;
            case WAITING_FEEDBACK ->
                    newStatus == TicketStatus.FEEDBACKED || newStatus == TicketStatus.CLOSED;
            case FEEDBACKED ->
                    newStatus == TicketStatus.CLOSED;
            case REJECTED, CLOSED ->
                    false; // 已驳回/关闭状态不允许转换
        };
    }


    @Transactional
    public TicketDetailDto addImage(Long ticketId, TicketImageRequest request) {
        RepairTicket ticket = findTicket(ticketId);
        TicketImage image = new TicketImage();
        image.setTicket(ticket);
        image.setImageUrl(request.imageUrl());
        imageRepository.save(image);
        return toDetailDto(ticket);
    }

    @Transactional
    public TicketDetailDto rateTicket(Long ticketId, TicketRatingRequest request) {
        try {
            // 使用悲观锁防止并发评价
            RepairTicket ticket = findTicketWithLock(ticketId);
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
            ratingRepository.save(rating);

            TicketStatus oldStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.FEEDBACKED);
            ticket.setClosedAt(LocalDateTime.now());
            appendStatusLog(ticket, oldStatus, TicketStatus.FEEDBACKED, ticket.getStudent());
            notifyTicketRated(ticket, request.score());
            return toDetailDto(ticket);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("工单已被其他用户修改，请刷新后重试");
        }
    }

    // 新增方法：更新维修备注
    @Transactional
    public TicketDetailDto updateRepairNotes(Long ticketId, String repairNotes, String operatorId) {
        RepairTicket ticket = findTicket(ticketId);
        User operator = userService.loadActiveUser(operatorId);
        
        ticket.setRepairNotes(repairNotes);
        ticketRepository.save(ticket);
        
        // 记录日志
        appendStatusLog(ticket, ticket.getStatus(), ticket.getStatus(), operator);
        return toDetailDto(ticket);
    }

    // 新增方法：更新处理备注
    @Transactional
    public TicketDetailDto updateProcessNotes(Long ticketId, String processNotes, String operatorId) {
        RepairTicket ticket = findTicket(ticketId);
        User operator = userService.loadActiveUser(operatorId);
        
        ticket.setProcessNotes(processNotes);
        ticketRepository.save(ticket);
        
        // 记录日志
        appendStatusLog(ticket, ticket.getStatus(), ticket.getStatus(), operator);
        return toDetailDto(ticket);
    }

    // 新增方法：设置预计完成时间
    @Transactional
    public TicketDetailDto setEstimatedCompletionTime(Long ticketId, LocalDateTime estimatedTime, String operatorId) {
        RepairTicket ticket = findTicket(ticketId);
        User operator = userService.loadActiveUser(operatorId);
        
        ticket.setEstimatedCompletionTime(estimatedTime);
        ticketRepository.save(ticket);
        
        // 记录日志
        appendStatusLog(ticket, ticket.getStatus(), ticket.getStatus(), operator);
        return toDetailDto(ticket);
    }

    @Transactional
    public TicketDetailDto getTicketDetail(Long ticketId) {
        RepairTicket ticket = findTicket(ticketId);
        return toDetailDto(ticket);
    }

    @Transactional(readOnly = true)
    public List<StaffRecommendationDto> recommendStaffForTicket(Long ticketId) {
        RepairTicket targetTicket = findTicket(ticketId);
        String targetCategory = targetTicket.getCategory() != null
            ? targetTicket.getCategory().getCategoryName()
            : null;

        List<User> staffUsers = userRepository.findByRoleAndIsActiveTrue(UserRole.STAFF);

        return staffUsers.stream()
            .map(staff -> buildStaffRecommendation(staff, targetCategory))
            .sorted(Comparator.comparingDouble(StaffRecommendationDto::score).reversed())
            .toList();
    }

    private StaffRecommendationDto buildStaffRecommendation(User staff, String targetCategory) {
        String staffId = staff.getUserId();
        int activeTaskCount = toInt(ticketRepository.countActiveTasksByStaffId(staffId));
        int sameCategoryCompletedCount = targetCategory == null
            ? 0
            : toInt(ticketRepository.countCompletedTasksByStaffIdAndCategory(staffId, targetCategory));
        int completedTaskCount = toInt(ticketRepository.countCompletedTasksByStaffId(staffId));
        double averageRating = safeDouble(ticketRepository.findAverageRatingByStaffId(staffId), 0.0);
        double averageProcessingHours = safeDouble(ticketRepository.findAverageProcessingHoursByStaffId(staffId), 72.0);

        double loadScore = Math.max(0, 1.0 - Math.min(activeTaskCount, 8) / 8.0) * 35.0;
        double categoryScore = Math.min(sameCategoryCompletedCount, 5) / 5.0 * 25.0;
        double ratingScore = averageRating > 0 ? averageRating / 5.0 * 20.0 : 12.0;
        double speedScore = Math.max(0, 1.0 - Math.min(averageProcessingHours, 120.0) / 120.0) * 20.0;
        double score = Math.round((loadScore + categoryScore + ratingScore + speedScore) * 10.0) / 10.0;

        String reason = "当前待办 " + activeTaskCount
            + " 单，同类完成 " + sameCategoryCompletedCount
            + " 单，平均评分 " + String.format(java.util.Locale.ROOT, "%.1f", averageRating)
            + "，平均处理 " + String.format(java.util.Locale.ROOT, "%.1f", averageProcessingHours) + " 小时";

        return new StaffRecommendationDto(
            staff.getUserId(),
            safeUserName(staff),
            staff.getContactPhone(),
            score,
            activeTaskCount,
            sameCategoryCompletedCount,
            completedTaskCount,
            Math.round(averageRating * 10.0) / 10.0,
            Math.round(averageProcessingHours * 10.0) / 10.0,
            reason
        );
    }

    /**
     * 学生删除自己的报修单：物理删除（从数据库中彻底删除）
     * 仅允许删除待受理状态的工单
     */
    @Transactional
    public void deleteTicket(Long ticketId, String studentId) {
        try {
            User student = userService.loadActiveUser(studentId);
            // 使用悲观锁防止并发删除
            RepairTicket ticket = findTicketWithLock(ticketId);

            // 必须是本人提交的工单
            if (ticket.getStudent() == null ||
                    !Objects.equals(ticket.getStudent().getUserId(), student.getUserId())) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "只能删除自己提交的报修单");
            }

            // 仅允许删除待受理状态的工单
            if (ticket.getStatus() != TicketStatus.WAITING_ACCEPT) {
                throw new BusinessException("仅待受理状态的报修单可以删除");
            }

            // 先删除关联的图片
            List<TicketImage> images = ticket.getImages();
            if (images != null && !images.isEmpty()) {
                for (TicketImage image : images) {
                    // 删除文件系统中的图片文件
                    String imageUrl = image.getImageUrl();
                    if (imageUrl != null && imageUrl.startsWith("/uploads/")) {
                        try {
                            String filename = imageUrl.substring("/uploads/".length());
                            Path filePath = Paths.get(uploadPath, filename);
                            if (Files.exists(filePath)) {
                                Files.delete(filePath);
                            }
                        } catch (IOException e) {
                            // 图片文件清理失败不阻断工单删除，数据库记录仍按主流程处理。
                        }
                    }
                    // 删除数据库中的图片记录
                    imageRepository.delete(image);
                }
            }

            // 删除关联的状态日志
            List<TicketStatusLog> statusLogs = ticket.getStatusLogs();
            if (statusLogs != null && !statusLogs.isEmpty()) {
                statusLogRepository.deleteAll(statusLogs);
            }

            // 删除关联的评价（如果存在）
            Optional<Rating> ratingOpt = ratingRepository.findByTicket(ticket);
            if (ratingOpt.isPresent()) {
                ratingRepository.delete(ratingOpt.get());
            }

            // 最后删除工单本身（物理删除）
            ticketRepository.delete(ticket);
            ticketRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("工单已被其他用户修改，请刷新后重试");
        }
    }

    @Transactional
    public List<TicketSummaryDto> listByStudent(String studentId) {
        User student = userService.loadActiveUser(studentId);
        return ticketRepository.findByStudent(student)
            .stream()
            .sorted(Comparator.comparing(RepairTicket::getCreatedAt).reversed())
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<TicketSummaryDto> listByStaff(String staffId) {
        User staff = userService.loadActiveUser(staffId);

        // 查询分配给该维修工的所有任务
        List<RepairTicket> tickets = ticketRepository.findByStaff(staff);

        return tickets.stream()
            .sorted(Comparator.comparing(RepairTicket::getCreatedAt).reversed())
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<TicketSummaryDto> listByStatus(TicketStatus status) {
        return ticketRepository.findByStatus(status)
            .stream()
            .sorted(Comparator.comparing(RepairTicket::getCreatedAt).reversed())
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<TicketSummaryDto> listAll() {
        return listAll(false);
    }

    @Transactional
    public List<TicketSummaryDto> listAll(boolean includeDeleted) {
        List<RepairTicket> tickets = ticketRepository.findAll();
        return tickets.stream()
            .filter(t -> includeDeleted || !Boolean.TRUE.equals(t.getDeleted()))
            .sorted(Comparator.comparing(RepairTicket::getCreatedAt).reversed())
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public Long countByCategory(Long categoryId) {
        return ticketRepository.countByCategoryId(categoryId);
    }
    
    @Transactional(readOnly = true)
    public Long countByStatus(TicketStatus status) {
        return ticketRepository.countByStatus(status);
    }

    // 新增方法：获取位置报修数量排行
    @Transactional(readOnly = true)
    public List<LocationStatsDto> getLocationStats() {
        List<Object[]> results = ticketRepository.findLocationStats();
        return results.stream()
            .map(row -> new LocationStatsDto(
                (String) row[0],
                ((Long) row[1]).intValue()
            ))
            // 限制返回前10条记录
            .limit(10)
            .collect(Collectors.toList());
    }

    // 新增方法：获取维修人员评分排行
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
            // 限制返回前10条记录
            .limit(10)
            .collect(Collectors.toList());
    }

    // 新增方法：基于视图获取按类别的统计数据，供 DataAnalysis 使用
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCategoryStatsFromView() {
        List<Object[]> results = ticketRepository.findCategoryStatsFromView();
        List<Map<String, Object>> stats = new ArrayList<>();

        for (Object[] row : results) {
            String categoryKey = (String) row[0];
            String categoryName = (String) row[1];
            // 原生 SQL 聚合函数在 MySQL 中通常返回 BigDecimal，这里统一用 Number 再转为整数，避免 ClassCastException
            Number totalTicketsNum = (Number) row[2];
            Number completedTicketsNum = (Number) row[3];
            Number ratedTicketsNum = (Number) row[4];
            Number avgRatingNum = (Number) row[5];

            Map<String, Object> item = new HashMap<>();
            // 前端 DataAnalysis 目前使用的字段
            item.put("category", categoryName);
            item.put("name", categoryName);
            item.put("type", categoryName);
            int total = totalTicketsNum != null ? totalTicketsNum.intValue() : 0;
            item.put("count", total);
            item.put("value", total);

            // 额外提供更详细的统计信息，便于后续扩展
            item.put("categoryKey", categoryKey);
            item.put("totalTickets", total);
            item.put("completedTickets", completedTicketsNum != null ? completedTicketsNum.intValue() : 0);
            item.put("ratedTickets", ratedTicketsNum != null ? ratedTicketsNum.intValue() : 0);
            item.put("avgRating", avgRatingNum != null ? avgRatingNum.doubleValue() : 0.0);

            stats.add(item);
        }

        return stats;
    }

    // 新增方法：获取月度统计数据
    @Transactional(readOnly = true)
    public Map<String, Object> getMonthlyStats() {
        // 获取最近12个月的统计数据
        LocalDate now = LocalDate.now();
        Map<String, Object> monthlyStats = new HashMap<>();
        
        // 初始化月度数据
        List<Map<String, Object>> monthsData = new ArrayList<>();
        
        for (int i = 11; i >= 0; i--) {
            LocalDate month = now.minusMonths(i);
            String monthStr = month.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            
            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", monthStr);
            monthData.put("count", ticketRepository.countByMonth(monthStr));
            monthsData.add(monthData);
        }
        
        monthlyStats.put("months", monthsData);
        
        // 统计各类状态的数量
        Map<String, Long> statusStats = new HashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            statusStats.put(status.name(), ticketRepository.countByStatus(status));
        }
        monthlyStats.put("statusDistribution", statusStats);

        return monthlyStats;
    }

    // 新增方法：获取平均处理时间（从创建到完成）
    @Transactional(readOnly = true)
    public Map<String, Object> getAverageProcessingTime() {
        Double avgHours = ticketRepository.findAverageProcessingTimeHours();
        Long completedCount = ticketRepository.countCompletedTickets();

        Map<String, Object> result = new HashMap<>();
        result.put("completedTickets", completedCount);

        if (avgHours != null && avgHours > 0 && completedCount > 0) {
            // 将小时转换为更友好的格式
            double totalHours = avgHours;

            if (totalHours >= 24) {
                // 超过24小时，显示天数
                double days = totalHours / 24;
                result.put("avgHours", Math.round(totalHours));
                result.put("avgDays", Math.round(days * 10) / 10.0); // 保留一位小数
                result.put("displayText", String.format("约 %.1f 天", days));
            } else {
                // 少于24小时，显示小时
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

    private Map<String, Object> buildSlaItem(RepairTicket ticket, LocalDateTime now) {
        String priority = normalizePriority(ticket.getPriority());
        long responseHours = responseLimitHours(priority);
        long completionHours = completionLimitHours(priority);

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
        long warningHours = Math.max(1, Math.round(limitHours * 0.25));
        boolean warning = !overdue && !now.isBefore(dueAt.minusHours(warningHours));
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
        rules.add(buildSlaRule("high", 2, 24));
        rules.add(buildSlaRule("medium", 8, 72));
        rules.add(buildSlaRule("low", 24, 168));
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
        String normalized = priority.toLowerCase(Locale.ROOT);
        if ("high".equals(normalized) || "medium".equals(normalized) || "low".equals(normalized)) {
            return normalized;
        }
        return "medium";
    }

    private long responseLimitHours(String priority) {
        return switch (priority) {
            case "high" -> configLong("sla.high.responseHours", 2);
            case "low" -> configLong("sla.low.responseHours", 24);
            default -> configLong("sla.medium.responseHours", 8);
        };
    }

    private long completionLimitHours(String priority) {
        return switch (priority) {
            case "high" -> configLong("sla.high.completionHours", 24);
            case "low" -> configLong("sla.low.completionHours", 168);
            default -> configLong("sla.medium.completionHours", 72);
        };
    }

    private long configLong(String key, long fallback) {
        try {
            String value = systemConfigService.getValue(key, String.valueOf(fallback));
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private RepairTicket findTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "报修单不存在"));
    }

    /**
     * 使用悲观锁查找工单（用于关键操作，防止并发修改）
     */
    private RepairTicket findTicketWithLock(Long ticketId) {
        return ticketRepository.findByIdWithLock(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "报修单不存在"));
    }

    private void appendStatusLog(RepairTicket ticket, TicketStatus oldStatus, TicketStatus newStatus, User operator) {
        TicketStatusLog log = new TicketStatusLog();
        log.setTicket(ticket);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setChangedBy(operator);
        log.setLogTime(LocalDateTime.now());
        statusLogRepository.save(log);
    }

    private void notifyAdminsNewTicket(RepairTicket ticket) {
        notificationService.notifyAdmins(
            "新报修工单待分配",
            "工单 #" + ticket.getTicketId() + " 已提交，地点：" + safeText(ticket.getLocationText()) + "，请及时分配维修人员。",
            ticket
        );
    }

    private void notifyTicketAssigned(RepairTicket ticket, User staff) {
        notificationService.notifyUser(
            staff,
            "你有新的维修任务",
            "工单 #" + ticket.getTicketId() + " 已分配给你，地点：" + safeText(ticket.getLocationText()) + "。",
            ticket
        );
        notificationService.notifyUser(
            ticket.getStudent(),
            "报修工单已受理",
            "你的工单 #" + ticket.getTicketId() + " 已分配给维修人员：" + safeUserName(staff) + "。",
            ticket
        );
    }

    private void notifyStatusChanged(RepairTicket ticket, TicketStatus oldStatus, TicketStatus newStatus, User oldStaff) {
        if (oldStatus == newStatus) {
            return;
        }
        switch (newStatus) {
            case REJECTED -> {
                String reason = ticket.getRejectionReason() == null || ticket.getRejectionReason().isBlank()
                    ? "未填写具体原因"
                    : ticket.getRejectionReason();
                notificationService.notifyUser(
                    ticket.getStudent(),
                    "报修工单被驳回",
                    "你的工单 #" + ticket.getTicketId() + " 已被驳回，原因：" + reason,
                    ticket
                );
                notificationService.notifyUser(
                    oldStaff,
                    "维修任务已驳回关闭",
                    "工单 #" + ticket.getTicketId() + " 已驳回，不再需要继续处理。",
                    ticket
                );
            }
            case RESOLVED, WAITING_FEEDBACK -> notificationService.notifyUser(
                ticket.getStudent(),
                "报修工单已完成",
                "你的工单 #" + ticket.getTicketId() + " 已处理完成，请确认并评价本次服务。",
                ticket
            );
            case CLOSED -> {
                notificationService.notifyUser(
                    ticket.getStudent(),
                    "报修工单已关闭",
                    "你的工单 #" + ticket.getTicketId() + " 已关闭。",
                    ticket
                );
                notificationService.notifyUser(
                    ticket.getStaff(),
                    "维修任务已关闭",
                    "工单 #" + ticket.getTicketId() + " 已关闭归档。",
                    ticket
                );
            }
            default -> {
            }
        }
    }

    private void notifyTicketRated(RepairTicket ticket, Integer score) {
        notificationService.notifyUser(
            ticket.getStaff(),
            "学生已完成评价",
            "工单 #" + ticket.getTicketId() + " 收到学生评价：" + score + " 星。",
            ticket
        );
        notificationService.notifyAdmins(
            "工单评价已提交",
            "工单 #" + ticket.getTicketId() + " 已完成评价，评分：" + score + " 星。",
            ticket
        );
    }

    private String safeUserName(User user) {
        if (user == null) {
            return "未指定";
        }
        return user.getNickname() != null && !user.getNickname().isBlank()
            ? user.getNickname()
            : user.getUserId();
    }

    private String safeText(String text) {
        return text == null || text.isBlank() ? "未填写" : text;
    }

    private int toInt(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private int toInt(Number value) {
        return value == null ? 0 : value.intValue();
    }

    private double safeDouble(Double value, double fallback) {
        return value == null || value.isNaN() || value.isInfinite() ? fallback : value;
    }

    private TicketDetailDto toDetailDto(RepairTicket ticket) {
        List<TicketImageDto> images = imageRepository.findByTicket(ticket)
            .stream()
            .map(image -> new TicketImageDto(image.getImageId(), image.getImageUrl(), image.getUploadedAt()))
            .collect(Collectors.toList());
        List<TicketStatusLogDto> logs = statusLogRepository.findByTicketOrderByLogTimeAsc(ticket)
            .stream()
            .map(log -> new TicketStatusLogDto(
                log.getLogId(),
                log.getOldStatus(),
                log.getNewStatus(),
                log.getChangedBy() != null ? log.getChangedBy().getUserId() : null,
                log.getLogTime()
            ))
            .collect(Collectors.toList());
        RatingDto ratingDto = ratingRepository.findByTicket(ticket)
            .map(rating -> new RatingDto(
                rating.getRatingId(),
                rating.getScore(),
                rating.getComment(),
                rating.getStudent() != null ? rating.getStudent().getUserId() : null,
                rating.getStudent() != null ? rating.getStudent().getNickname() : null,
                rating.getStaff() != null ? rating.getStaff().getUserId() : null,
                rating.getStaff() != null ? rating.getStaff().getNickname() : null,
                ticket.getTicketId(),
                rating.getSpeedRating(),
                rating.getQualityRating(),
                rating.getAttitudeRating(),
                rating.getResolved(),
                rating.getAnonymous(),
                rating.getRatedAt()
            ))
            .orElse(null);

        return new TicketDetailDto(
            ticket.getTicketId(),
            ticket.getStatus(),
            ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : null,
            ticket.getStudent() != null ? ticket.getStudent().getUserId() : null,
            ticket.getStudent() != null ? ticket.getStudent().getNickname() : null,
            ticket.getStaff() != null ? ticket.getStaff().getUserId() : null,
            ticket.getStaff() != null ? ticket.getStaff().getNickname() : null,
            ticket.getLocationText(),
            ticket.getDescription(),
            ticket.getRejectionReason(),
            ticket.getPriority(),
            ticket.getRepairNotes(),
            ticket.getProcessNotes(),
            ticket.getEstimatedCompletionTime(),
            ticket.getCreatedAt(),
            ticket.getAssignedAt(),
            ticket.getCompletedAt(),
            ticket.getClosedAt(),
            images,
            logs,
            ratingDto
        );
    }

    private TicketSummaryDto toSummaryDto(RepairTicket ticket) {
        // 查询该工单的评分（如果存在），用于给维修工统计平均分
        Integer ratingScore = ratingRepository.findByTicket(ticket)
            .map(Rating::getScore)
            .orElse(null);

        return new TicketSummaryDto(
            ticket.getTicketId(),
            ticket.getStatus(),
            ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : null,
            ticket.getStudent() != null ? ticket.getStudent().getUserId() : null,
            ticket.getStaff() != null ? ticket.getStaff().getUserId() : null,
            ticket.getLocationText(),
            ticket.getDescription(),
            ticket.getPriority(),
            ticket.getCreatedAt(),
            ticket.getAssignedAt(),
            ticket.getEstimatedCompletionTime(),
            ratingScore,
            ticket.getDeleted(),
            ticket.getDeletedAt()
        );
    }

    private String safeName(User user) {
        if (user == null) {
            return "未指定人员";
        }
        return user.getNickname() != null && !user.getNickname().isBlank()
            ? user.getNickname()
            : user.getUserId();
    }

    // ==================== 维修工相关方法 ====================

    /**
     * 维修工主动接单
     * 将 WAITING_ACCEPT 状态的工单分配给自己，状态变为 IN_PROGRESS
     *
     * @param ticketId 工单ID
     * @param staffId  维修工ID
     * @return 工单详情
     */
    @Transactional
    public TicketDetailDto acceptTicket(Long ticketId, String staffId) {
        try {
            // 使用悲观锁防止并发接单
            RepairTicket ticket = findTicketWithLock(ticketId);
            User staff = userService.loadActiveUser(staffId);

            // 验证维修工角色
            if (staff.getRole() != UserRole.STAFF) {
                throw new BusinessException("仅维修工角色可以接单");
            }

            // 验证工单状态
            if (ticket.getStatus() != TicketStatus.WAITING_ACCEPT) {
                throw new BusinessException("当前工单状态不允许接单，仅待受理状态的工单可以接单");
            }

            // 验证工单未被分配
            if (ticket.getStaff() != null) {
                throw new BusinessException("该工单已被分配，无法接单");
            }

            // 分配工单并更新状态
            TicketStatus oldStatus = ticket.getStatus();
            ticket.setStaff(staff);
            ticket.setAssignedAt(LocalDateTime.now());
            ticket.setStatus(TicketStatus.IN_PROGRESS);

            // 记录状态变更日志
            appendStatusLog(ticket, oldStatus, TicketStatus.IN_PROGRESS, staff);

            // 发送通知
            notifyTicketAssigned(ticket, staff);

            log.info("维修工接单成功: ticketId={}, staffId={}", ticketId, staffId);
            return toDetailDto(ticket);

        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("工单已被其他用户修改，请刷新后重试");
        }
    }

    /**
     * 维修工完成工单
     * 将 IN_PROGRESS 状态的工单更新为 RESOLVED
     *
     * @param ticketId       工单ID
     * @param staffId        维修工ID
     * @param repairNotes    维修备注（可选）
     * @return 工单详情
     */
    @Transactional
    public TicketDetailDto resolveTicket(Long ticketId, String staffId, String repairNotes) {
        try {
            // 使用悲观锁防止并发操作
            RepairTicket ticket = findTicketWithLock(ticketId);
            User staff = userService.loadActiveUser(staffId);

            // 验证工单归属
            if (ticket.getStaff() == null || !ticket.getStaff().getUserId().equals(staffId)) {
                throw new BusinessException("只能完成分配给自己的工单");
            }

            // 验证工单状态
            if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
                throw new BusinessException("当前工单状态不允许完成，仅处理中状态的工单可以完成");
            }

            // 更新维修备注
            if (repairNotes != null && !repairNotes.isBlank()) {
                ticket.setRepairNotes(repairNotes);
            }

            // 更新状态为已处理
            TicketStatus oldStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.RESOLVED);
            ticket.setCompletedAt(LocalDateTime.now());

            // 记录状态变更日志
            appendStatusLog(ticket, oldStatus, TicketStatus.RESOLVED, staff);

            // 发送通知
            notifyStatusChanged(ticket, oldStatus, TicketStatus.RESOLVED, staff);

            log.info("维修工完成工单: ticketId={}, staffId={}", ticketId, staffId);
            return toDetailDto(ticket);

        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("工单已被其他用户修改，请刷新后重试");
        }
    }

    /**
     * 获取维修工工作台统计数据
     */
    @Transactional(readOnly = true)
    public StaffDashboardDto getStaffDashboard(String staffId) {
        // 1. 各状态工单数量
        Long pendingCount = ticketRepository.countByStaffIdAndStatus(staffId, "WAITING_ACCEPT");
        Long inProgressCount = ticketRepository.countByStaffIdAndStatus(staffId, "IN_PROGRESS");
        Long completedCount = ticketRepository.countCompletedTasksByStaffId(staffId);

        // 2. 时间维度完成数量（使用数据库无关的时间范围查询）
        LocalDateTime now = LocalDateTime.now();

        // 今日：当天 00:00:00 到下一天 00:00:00
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        Long todayCompleted = ticketRepository.countCompletedByStaffIdAndCompletedAtBetween(
                staffId, todayStart, todayEnd);

        // 本周：周一 00:00:00 到下周一 00:00:00
        LocalDateTime weekStart = now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusWeeks(1);
        Long weekCompleted = ticketRepository.countCompletedByStaffIdAndCompletedAtBetween(
                staffId, weekStart, weekEnd);

        // 本月：当月第一天 00:00:00 到下个月第一天 00:00:00
        LocalDateTime monthStart = now.toLocalDate()
                .with(TemporalAdjusters.firstDayOfMonth())
                .atStartOfDay();
        LocalDateTime monthEnd = now.toLocalDate()
                .with(TemporalAdjusters.firstDayOfNextMonth())
                .atStartOfDay();
        Long monthCompleted = ticketRepository.countCompletedByStaffIdAndCompletedAtBetween(
                staffId, monthStart, monthEnd);

        // 3. 评价统计
        Long totalRatingCount = 0L;
        Double avgRating = 0.0;
        try {
            Object ratingStatsRaw = ticketRepository.findRatingStatsByStaffId(staffId);
            if (ratingStatsRaw instanceof Object[]) {
                Object[] row = (Object[]) ratingStatsRaw;
                if (row.length >= 2) {
                    if (row[0] != null) {
                        totalRatingCount = ((Number) row[0]).longValue();
                    }
                    if (row[1] != null) {
                        avgRating = ((Number) row[1]).doubleValue();
                    }
                }
            }
        } catch (Exception e) {
            // 忽略解析错误，使用默认值
        }

        // 4. 优先级分布
        Map<String, Long> priorityDistribution = new HashMap<>();
        List<Object[]> priorityStats = ticketRepository.findPriorityDistributionByStaffId(staffId);
        for (Object[] row : priorityStats) {
            String priority = row[0] != null ? row[0].toString() : "未设置";
            Long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            priorityDistribution.put(priority, count);
        }

        // 5. 分类分布
        Map<String, Long> categoryDistribution = new HashMap<>();
        List<Object[]> categoryStats = ticketRepository.findCategoryDistributionByStaffId(staffId);
        for (Object[] row : categoryStats) {
            String category = row[0] != null ? row[0].toString() : "未分类";
            Long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            categoryDistribution.put(category, count);
        }

        // 6. 平均处理时长
        Double avgProcessingHours = ticketRepository.findAverageProcessingHoursByStaffId(staffId);
        if (avgProcessingHours == null) {
            avgProcessingHours = 0.0;
        }

        return new StaffDashboardDto(
            pendingCount != null ? pendingCount : 0L,
            inProgressCount != null ? inProgressCount : 0L,
            completedCount != null ? completedCount : 0L,
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

    /**
     * 分页获取维修工的任务列表
     */
    @Transactional(readOnly = true)
    public Map<String, Object> listStaffTasksPage(String staffId, TicketStatus status, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<RepairTicket> ticketPage = ticketRepository.findByStaffIdWithFilter(staffId, status, pageable);

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
}
