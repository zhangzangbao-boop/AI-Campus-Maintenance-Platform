package com.ligong.reportingcenter.service;

import com.ligong.reportingcenter.domain.entity.RepairProcessRecord;
import com.ligong.reportingcenter.domain.entity.RepairTicket;
import com.ligong.reportingcenter.domain.entity.User;
import com.ligong.reportingcenter.domain.enums.RepairProcessActionType;
import com.ligong.reportingcenter.domain.enums.TicketStatus;
import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.RepairProcessRecordDto;
import com.ligong.reportingcenter.dto.request.RepairProcessRecordRequest;
import com.ligong.reportingcenter.exception.BusinessException;
import com.ligong.reportingcenter.repository.RepairProcessRecordRepository;
import com.ligong.reportingcenter.repository.TicketRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RepairProcessRecordService {

    private final RepairProcessRecordRepository processRecordRepository;
    private final TicketRepository ticketRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final TicketService ticketService;

    @Transactional(readOnly = true)
    public List<RepairProcessRecordDto> listRecords(Long ticketId, String userId) {
        RepairTicket ticket = findTicket(ticketId);
        User currentUser = userService.loadActiveUser(userId);
        assertCanRead(ticket, currentUser);
        return processRecordRepository.findByTicketOrderByCreatedAtAsc(ticket)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public RepairProcessRecordDto addRecord(Long ticketId, String userId, RepairProcessRecordRequest request) {
        RepairTicket ticket = findTicket(ticketId);
        User staff = userService.loadActiveUser(userId);
        assertCanAdd(ticket, staff);

        RepairProcessRecord record = new RepairProcessRecord();
        record.setTicket(ticket);
        record.setStaff(staff);
        record.setActionType(request.actionType());
        record.setContent(request.content().trim());
        record.setImageUrl(request.imageUrl());
        record.setCreatedAt(LocalDateTime.now());
        processRecordRepository.save(record);

        notifyRecordCreated(ticket, staff, request.actionType(), record.getContent());
        return toDto(record);
    }

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
        User admin = userService.loadActiveUser(adminId);
        User originalStaff = requestRecord.getStaff();
        String content;
        if (approved) {
            if (newStaffId == null || newStaffId.isBlank()) {
                throw new BusinessException("审批通过时必须指定新的维修人员");
            }
            ticketService.assignTicket(ticket.getTicketId(), new com.ligong.reportingcenter.dto.request.TicketAssignRequest(adminId, newStaffId));
            User newStaff = userService.loadActiveUser(newStaffId);
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

    private RepairTicket findTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
    }

    private void assertCanRead(RepairTicket ticket, User user) {
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

    private void assertCanAdd(RepairTicket ticket, User user) {
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
        User staff,
        RepairProcessActionType actionType,
        String content
    ) {
        String title = switch (actionType) {
            case ARRIVED -> "维修人员已到场";
            case MATERIAL_USED -> "维修耗材记录已更新";
            case FINISHED -> "维修完成记录已更新";
            case TRANSFER_REQUEST -> "维修转派申请已提交";
            case TRANSFER_APPROVED -> "维修转派申请已通过";
            case TRANSFER_REJECTED -> "维修转派申请未通过";
        };
        String shortContent = content.length() > 80 ? content.substring(0, 80) + "..." : content;
        String message = "工单 #" + ticket.getTicketId() + "：" + shortContent;

        notificationService.notifyUser(ticket.getStudent(), title, message, ticket);
        notificationService.notifyAdmins(title, message, ticket);
        if (actionType == RepairProcessActionType.TRANSFER_REQUEST) {
            notificationService.notifyUser(staff, "转派申请已提交", message, ticket);
        }
    }

    private RepairProcessRecordDto toDto(RepairProcessRecord record) {
        User staff = record.getStaff();
        return new RepairProcessRecordDto(
            record.getRecordId(),
            record.getTicket() != null ? record.getTicket().getTicketId() : null,
            staff != null ? staff.getUserId() : null,
            staff != null ? staff.getNickname() : null,
            record.getActionType(),
            record.getContent(),
            record.getImageUrl(),
            record.getCreatedAt()
        );
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

    private String safeName(User user) {
        if (user == null) {
            return "未指定人员";
        }
        return user.getNickname() != null && !user.getNickname().isBlank()
            ? user.getNickname()
            : user.getUserId();
    }
}
