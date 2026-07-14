package com.qiyun.repairservice.controller;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.repairservice.domain.enums.TicketCommentType;
import com.qiyun.repairservice.dto.TicketCommentDto;
import com.qiyun.repairservice.dto.request.TicketCommentRequest;
import com.qiyun.repairservice.service.TicketCommentService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/repair-orders/{ticketId}/comments")
public class TicketCommentController {

    private final TicketCommentService ticketCommentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
    public Map<String, Object> list(@PathVariable("ticketId") Long ticketId) {
        List<TicketCommentDto> comments = ticketCommentService.listComments(ticketId, currentUserId());
        return success("工单沟通记录获取成功", comments);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
    public Map<String, Object> add(@PathVariable("ticketId") Long ticketId,
                                   @Valid @RequestBody TicketCommentRequest request) {
        TicketCommentDto comment = ticketCommentService.addComment(ticketId, currentUserId(), request);
        return success("工单沟通记录已提交", comment);
    }

    @PostMapping("/urge")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> urge(@PathVariable("ticketId") Long ticketId,
                                    @Valid @RequestBody TicketCommentRequest request) {
        TicketCommentRequest urgeRequest = new TicketCommentRequest(
            request.content(),
            TicketCommentType.URGE,
            request.imageUrl()
        );
        TicketCommentDto comment = ticketCommentService.addComment(ticketId, currentUserId(), urgeRequest);
        return success("催单已提交", comment);
    }

    private Map<String, Object> success(String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录后再访问");
        }
        return authentication.getName();
    }
}