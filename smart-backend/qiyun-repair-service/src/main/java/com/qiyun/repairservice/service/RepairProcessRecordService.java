package com.qiyun.repairservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.repairservice.domain.entity.RepairProcessRecord;
import com.qiyun.repairservice.domain.entity.RepairProcessRecordImage;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.RepairProcessActionType;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.dto.RepairProcessRecordDto;
import com.qiyun.repairservice.dto.request.RepairProcessRecordRequest;
import com.qiyun.repairservice.dto.request.TicketAssignRequest;
import com.qiyun.repairservice.repository.RepairProcessRecordImageRepository;
import com.qiyun.repairservice.repository.RepairProcessRecordRepository;
import com.qiyun.repairservice.repository.TicketRepository;
import com.qiyun.repairservice.repository.UserReferenceRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 维修过程记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepairProcessRecordService {

    private final RepairProcessRecordRepository processRecordRepository;
    private final RepairProcessRecordImageRepository processRecordImageRepository;
    private final TicketRepository ticketRepository;
    private final UserReferenceRepository userReferenceRepository;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final TicketService ticketService;

    @Transactional(readOnly = true)
    public List<RepairProcessRecordDto> listRecords(Long ticketId, String userId) {
        RepairTicket ticket = findTicket(ticketId);
        UserReference currentUser = loadActiveUser(userId);
        assertCanRead(ticket, currentUser);
        return processRecordRepository.findByTicketOrderByCreatedAtAsc(ticket)
            .stream()
            .map(this::toDto)
            .toList();
    }

    /**
     * 新增维修过程记录（增强版）
     */
    @Transactional
    public RepairProcessRecordDto addRecord(Long ticketId, String userId, RepairProcessRecordRequest request) {
        RepairTicket ticket = findTicket(ticketId);
        UserReference staff = loadActiveUser(userId);
        assertCanAdd(ticket, staff);

        RepairProcessRecord record = new RepairProcessRecord();
        record.setTicket(ticket);
        record.setStaff(staff);
        record.setActionType(request.actionType());
        record.setContent(request.content() != null ? request.content().trim() : "");
        List<String> imageUrls = normalizeImageUrls(request);
        record.setImageUrl(imageUrls.isEmpty() ? request.imageUrl() : imageUrls.get(0));
        record.setCreatedAt(LocalDateTime.now());

        // 设置新增字段
        if (request.arrivedAt() != null) {
            record.setArrivedAt(request.arrivedAt());
        }
        if (request.repairDescription() != null) {
            record.setRepairDescription(request.repairDescription().trim());
        }
        if (request.materialsUsed() != null) {
            record.setMaterialsUsed(request.materialsUsed());
        }
        if (request.finishedAt() != null) {
            record.setFinishedAt(request.finishedAt());
        }
        if (request.durationMinutes() != null) {
            record.setDurationMinutes(request.durationMinutes());
        }
        if (request.remarks() != null) {
            record.setRemarks(request.remarks().trim());
        }

        processRecordRepository.saveAndFlush(record);
        persistImages(record, imageUrls);
        log.info("新增维修过程记录: ticketId={}, staffId={}, actionType={}",
            ticketId, userId, request.actionType());

        notifyRecordCreated(ticket, staff, request.actionType(), record.getContent());
        return toDto(record);
    }

    // ==================== 快捷方法 ====================

    /**
     * Add a process record with images stored in the same request.
     */
    @Transactional
    public RepairProcessRecordDto addRecordWithImages(Long ticketId, String userId, RepairProcessRecordRequest request, List<MultipartFile> images) {
        RepairTicket ticket = findTicket(ticketId);
        UserReference staff = loadActiveUser(userId);
        assertCanAdd(ticket, staff);
        List<String> storedUrls = fileStorageService.storeImages(images);
        try {
            RepairProcessRecordRequest mergedRequest = new RepairProcessRecordRequest(
                request.actionType(),
                request.content(),
                storedUrls.isEmpty() ? request.imageUrl() : storedUrls.get(0),
                storedUrls.isEmpty() ? request.imageUrls() : storedUrls,
                request.arrivedAt(),
                request.repairDescription(),
                request.materialsUsed(),
                request.finishedAt(),
                request.durationMinutes(),
                request.remarks()
            );
            return addRecord(ticketId, userId, mergedRequest);
        } catch (RuntimeException e) {
            fileStorageService.deleteStoredFiles(storedUrls);
            throw e;
        }
    }

    /**
     * 记录到场确认
     */
    @Transactional
    public RepairProcessRecordDto recordArrival(Long ticketId, String staffId, String content, String imageUrl) {
        RepairProcessRecordRequest request = new RepairProcessRecordRequest(
            RepairProcessActionType.ARRIVED,
            content,
            imageUrl,
            LocalDateTime.now(),  // arrivedAt
            null,  // repairDescription
            null,  // materialsUsed
            null,  // finishedAt
            null,  // durationMinutes
            null   // remarks
        );
        return addRecord(ticketId, staffId, request);
    }

    /**
     * 记录维修过程（带详细描述）
     */
    @Transactional
    public RepairProcessRecordDto recordRepairProcess(
        Long ticketId,
        String staffId,
        String repairDescription,
        String materialsUsed,
        Integer durationMinutes,
        String remarks
    ) {
        RepairProcessRecordRequest request = new RepairProcessRecordRequest(
            RepairProcessActionType.REPAIRING,
            repairDescription != null ? repairDescription : "维修中",
            null,  // imageUrl
            null,  // arrivedAt
            repairDescription,
            materialsUsed,
            null,  // finishedAt
            durationMinutes,
            remarks
        );
        return addRecord(ticketId, staffId, request);
    }

    /**
     * 记录完成确认
     */
    @Transactional
    public RepairProcessRecordDto recordFinish(
        Long ticketId,
        String staffId,
        String repairDescription,
        String materialsUsed,
        Integer durationMinutes,
        String remarks
    ) {
        RepairProcessRecordRequest request = new RepairProcessRecordRequest(
            RepairProcessActionType.FINISHED,
            repairDescription != null ? repairDescription : "维修完成",
            null,  // imageUrl
            null,  // arrivedAt
            repairDescription,
            materialsUsed,
            LocalDateTime.now(),  // finishedAt
            durationMinutes,
            remarks
        );
        return addRecord(ticketId, staffId, request);
    }

    // ==================== 原有方法 ====================

    @Transactional(readOnly = true)
    public List<RepairProcessRecordDto> listTransferRequests(boolean pendingOnly) {
        List<RepairProcessRecord> records = processRecordRepository
            .findByActionTypeOrderByCreatedAtDesc(RepairProcessActionType.TRANSFER_REQUEST);
        if (!pendingOnly) {
            return records.stream().map(this::toDto).toList();
        }
        return records.stream()
            .filter(record -> !hasTransferDecision(record.getTicket()))
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public RepairProcessRecordDto decideTransferRequest(
        Long recordId,
        String adminId,
        boolean approved,
        String newStaffId,
        String reason
    ) {
        RepairProcessRecord requestRecord = processRecordRepository.findById(recordId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "转派申请不存在"));
        if (requestRecord.getActionType() != RepairProcessActionType.TRANSFER_REQUEST) {
            throw new BusinessException("该记录不是转派申请");
        }
        if (hasTransferDecision(requestRecord.getTicket())) {
            throw new BusinessException("该转派申请已处理");
        }

        RepairTicket ticket = requestRecord.getTicket();
        UserReference admin = loadActiveUser(adminId);
        UserReference originalStaff = requestRecord.getStaff();
        String content;
        if (approved) {
            if (newStaffId == null || newStaffId.isBlank()) {
                throw new BusinessException("审批通过时必须指定新的维修人员");
            }
            ticketService.assignTicket(ticket.getTicketId(), new TicketAssignRequest(adminId, newStaffId));
            UserReference newStaff = loadActiveUser(newStaffId);
            content = "转派申请已通过，工单已转派给 " + safeName(newStaff)
                + "。审批说明：" + safeReason(reason);
            notificationService.notifyUser(originalStaff, "转派申请已通过", content, ticket);
            notificationService.notifyUser(newStaff, "你收到一条转派任务", content, ticket);
            notificationService.notifyUser(ticket.getStudent(), "工单已转派", "你的工单已转派给新的维修人员处理。", ticket);
        } else {
            content = "转派申请未通过，请继续处理当前工单。审批说明：" + safeReason(reason);
            notificationService.notifyUser(originalStaff, "转派申请未通过", content, ticket);
        }

        RepairProcessRecord decision = new RepairProcessRecord();
        decision.setTicket(ticket);
        decision.setStaff(admin);
        decision.setActionType(approved ? RepairProcessActionType.TRANSFER_APPROVED : RepairProcessActionType.TRANSFER_REJECTED);
        decision.setContent(content);
        decision.setCreatedAt(LocalDateTime.now());
        processRecordRepository.save(decision);
        notificationService.notifyAdmins(approved ? "转派申请已通过" : "转派申请未通过", content, ticket);
        return toDto(decision);
    }

    // ==================== 私有方法 ====================

    private RepairTicket findTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
    }

    private UserReference loadActiveUser(String userId) {
        return userReferenceRepository.findByUserIdAndIsActiveTrue(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在或已禁用"));
    }

    private void assertCanRead(RepairTicket ticket, UserReference user) {
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }
        if (user.getRole() == UserRole.STUDENT
            && ticket.getStudent() != null
            && user.getUserId().equals(ticket.getStudent().getUserId())) {
            return;
        }
        if (user.getRole() == UserRole.STAFF
            && ticket.getStaff() != null
            && user.getUserId().equals(ticket.getStaff().getUserId())) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "当前账号无权查看该维修过程记录");
    }

    private void assertCanAdd(RepairTicket ticket, UserReference user) {
        if (user.getRole() != UserRole.STAFF) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有维修人员可以新增维修过程记录");
        }
        if (ticket.getStaff() == null || !user.getUserId().equals(ticket.getStaff().getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有当前工单负责人可以新增维修过程记录");
        }
        if (ticket.getStatus() == TicketStatus.WAITING_ACCEPT
            || ticket.getStatus() == TicketStatus.REJECTED
            || ticket.getStatus() == TicketStatus.CLOSED
            || ticket.getStatus() == TicketStatus.FEEDBACKED) {
            throw new BusinessException("当前工单状态不允许新增维修过程记录");
        }
    }

    private void notifyRecordCreated(
        RepairTicket ticket,
        UserReference staff,
        RepairProcessActionType actionType,
        String content
    ) {
        String title = switch (actionType) {
            case ARRIVED -> "维修人员已到场";
            case DIAGNOSING -> "维修人员正在诊断";
            case REPAIRING -> "维修进行中";
            case MATERIAL_USED -> "维修耗材记录已更新";
            case FINISHED -> "维修完成记录已更新";
            case PAUSED -> "维修已暂停";
            case TRANSFER_REQUEST -> "维修转派申请已提交";
            case TRANSFER_APPROVED -> "维修转派申请已通过";
            case TRANSFER_REJECTED -> "维修转派申请未通过";
        };
        String shortContent = content != null && content.length() > 80
            ? content.substring(0, 80) + "..."
            : (content != null ? content : "");
        String message = "工单 #" + ticket.getTicketId() + "：" + shortContent;

        notificationService.notifyUser(ticket.getStudent(), title, message, ticket);
        notificationService.notifyAdmins(title, message, ticket);
        if (actionType == RepairProcessActionType.TRANSFER_REQUEST) {
            notificationService.notifyUser(staff, "转派申请已提交", message, ticket);
        }
    }

    private RepairProcessRecordDto toDto(RepairProcessRecord record) {
        UserReference staff = record.getStaff();
        return new RepairProcessRecordDto(
            record.getRecordId(),
            record.getTicket() != null ? record.getTicket().getTicketId() : null,
            staff != null ? staff.getUserId() : null,
            staff != null ? staff.getNickname() : null,
            record.getActionType(),
            record.getActionType() != null ? record.getActionType().getDescription() : null,
            record.getContent(),
            record.getImageUrl(),
            imageUrlsFor(record),
            // 新增字段
            record.getArrivedAt(),
            record.getRepairDescription(),
            record.getMaterialsUsed(),
            record.getFinishedAt(),
            record.getDurationMinutes(),
            record.getRemarks(),
            record.getCreatedAt()
        );
    }


    private List<String> normalizeImageUrls(RepairProcessRecordRequest request) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            urls.add(request.imageUrl().trim());
        }
        if (request.imageUrls() != null) {
            request.imageUrls().stream()
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .forEach(urls::add);
        }
        if (urls.size() > FileStorageService.MAX_IMAGE_COUNT) {
            throw new BusinessException("每条记录最多上传 " + FileStorageService.MAX_IMAGE_COUNT + " 张图片");
        }
        return new ArrayList<>(urls);
    }

    private void persistImages(RepairProcessRecord record, List<String> imageUrls) {
        for (String imageUrl : imageUrls) {
            RepairProcessRecordImage image = new RepairProcessRecordImage();
            image.setRecord(record);
            image.setImageUrl(imageUrl);
            image.setCreatedAt(LocalDateTime.now());
            processRecordImageRepository.save(image);
        }
    }

    private List<String> imageUrlsFor(RepairProcessRecord record) {
        List<String> urls = new ArrayList<>(processRecordImageRepository.findByRecordOrderByImageIdAsc(record).stream()
            .map(RepairProcessRecordImage::getImageUrl)
            .toList());
        if (urls.isEmpty() && record.getImageUrl() != null && !record.getImageUrl().isBlank()) {
            urls.add(record.getImageUrl());
        }
        return urls;
    }
    private boolean hasTransferDecision(RepairTicket ticket) {
        if (ticket == null) {
            return true;
        }
        return processRecordRepository.findByTicketOrderByCreatedAtAsc(ticket).stream()
            .anyMatch(record -> record.getActionType() == RepairProcessActionType.TRANSFER_APPROVED
                || record.getActionType() == RepairProcessActionType.TRANSFER_REJECTED);
    }

    private String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "无" : reason.trim();
    }

    private String safeName(UserReference user) {
        if (user == null) {
            return "未指定人员";
        }
        return user.getNickname() != null && !user.getNickname().isBlank()
            ? user.getNickname()
            : user.getUserId();
    }
}
