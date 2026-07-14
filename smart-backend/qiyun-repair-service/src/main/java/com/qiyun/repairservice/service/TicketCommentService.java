package com.qiyun.repairservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.TicketComment;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.TicketCommentType;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.dto.TicketCommentDto;
import com.qiyun.repairservice.dto.request.TicketCommentRequest;
import com.qiyun.repairservice.repository.TicketCommentRepository;
import com.qiyun.repairservice.repository.TicketRepository;
import com.qiyun.repairservice.repository.UserReferenceRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工单评论/催单服务
 */
@Service
@RequiredArgsConstructor
public class TicketCommentService {

    private final TicketCommentRepository ticketCommentRepository;
    private final TicketRepository ticketRepository;
    private final UserReferenceRepository userReferenceRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<TicketCommentDto> listComments(Long ticketId, String userId) {
        RepairTicket ticket = findTicket(ticketId);
        UserReference currentUser = loadActiveUser(userId);
        assertCanAccess(ticket, currentUser);
        return ticketCommentRepository.findByTicketOrderByCreatedAtAsc(ticket)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public TicketCommentDto addComment(Long ticketId, String userId, TicketCommentRequest request) {
        RepairTicket ticket = findTicket(ticketId);
        UserReference currentUser = loadActiveUser(userId);
        assertCanAccess(ticket, currentUser);

        TicketCommentType type = normalizeType(currentUser, request.commentType());
        validateTypeAllowed(ticket, currentUser, type);

        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(currentUser);
        comment.setContent(request.content().trim());
        comment.setImageUrl(request.imageUrl());
        comment.setCommentType(type);
        comment.setCreatedAt(LocalDateTime.now());
        ticketCommentRepository.save(comment);

        sendCommentNotifications(ticket, currentUser, type, comment.getContent());
        return toDto(comment);
    }

    private RepairTicket findTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));
    }

    private UserReference loadActiveUser(String userId) {
        return userReferenceRepository.findByUserIdAndIsActiveTrue(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在或已禁用"));
    }

    private void assertCanAccess(RepairTicket ticket, UserReference user) {
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
        throw new BusinessException(HttpStatus.FORBIDDEN, "当前账号无权访问该工单");
    }

    private TicketCommentType normalizeType(UserReference user, TicketCommentType requestedType) {
        if (requestedType != null) {
            return requestedType;
        }
        return user.getRole() == UserRole.STUDENT ? TicketCommentType.SUPPLEMENT : TicketCommentType.REPLY;
    }

    private void validateTypeAllowed(RepairTicket ticket, UserReference user, TicketCommentType type) {
        if (type == TicketCommentType.URGE) {
            if (user.getRole() != UserRole.STUDENT) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "只有学生可以催单");
            }
            if (ticket.getStatus() == TicketStatus.REJECTED
                || ticket.getStatus() == TicketStatus.FEEDBACKED
                || ticket.getStatus() == TicketStatus.CLOSED) {
                throw new BusinessException("当前工单状态不允许催单");
            }
            return;
        }

        if (type == TicketCommentType.SUPPLEMENT && user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有学生可以补充说明");
        }
        if (type == TicketCommentType.REPLY && user.getRole() == UserRole.STUDENT) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "学生请使用补充说明或催单");
        }
    }

    private void sendCommentNotifications(RepairTicket ticket, UserReference author, TicketCommentType type, String content) {
        String title = switch (type) {
            case URGE -> "工单催办提醒";
            case SUPPLEMENT -> "学生补充了工单说明";
            case REPLY -> "工单收到新的处理回复";
        };
        String shortContent = content.length() > 80 ? content.substring(0, 80) + "..." : content;
        String message = "工单 #" + ticket.getTicketId() + "：" + shortContent;

        if (type == TicketCommentType.URGE || type == TicketCommentType.SUPPLEMENT) {
            notificationService.notifyAdmins(title, message, ticket);
            notifyIfDifferent(ticket.getStaff(), author, title, message, ticket);
            return;
        }

        notifyIfDifferent(ticket.getStudent(), author, title, message, ticket);
        notifyIfDifferent(ticket.getStaff(), author, title, message, ticket);
    }

    private void notifyIfDifferent(UserReference receiver, UserReference author, String title, String content, RepairTicket ticket) {
        if (receiver == null || author == null || receiver.getUserId().equals(author.getUserId())) {
            return;
        }
        notificationService.notifyUser(receiver, title, content, ticket);
    }

    private TicketCommentDto toDto(TicketComment comment) {
        UserReference author = comment.getAuthor();
        return new TicketCommentDto(
            comment.getCommentId(),
            comment.getTicket() != null ? comment.getTicket().getTicketId() : null,
            author != null ? author.getUserId() : null,
            author != null ? author.getNickname() : null,
            author != null ? author.getRole() : null,
            comment.getCommentType(),
            comment.getContent(),
            comment.getImageUrl(),
            comment.getCreatedAt()
        );
    }
}