package com.qiyun.repairservice.controller;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.repairservice.dto.RepairProcessRecordDto;
import com.qiyun.repairservice.service.RepairProcessRecordService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员转派审批接口
 * 迁移自 biz-service AdminOpsController
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminTransferController {

    private final RepairProcessRecordService repairProcessRecordService;

    /**
     * 获取转派申请列表
     * GET /api/admin/transfer-requests
     */
    @GetMapping("/transfer-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> listTransferRequests(
            @RequestParam(value = "pendingOnly", defaultValue = "true") boolean pendingOnly) {
        List<RepairProcessRecordDto> requests = repairProcessRecordService.listTransferRequests(pendingOnly);
        return success(requests, "获取成功");
    }

    /**
     * 审批转派申请
     * PUT /api/admin/transfer-requests/{recordId}/decision
     */
    @PutMapping("/transfer-requests/{recordId}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> decideTransferRequest(@PathVariable("recordId") Long recordId,
                                                     @RequestBody TransferDecisionRequest request) {
        RepairProcessRecordDto result = repairProcessRecordService.decideTransferRequest(
            recordId,
            currentUserId(),
            Boolean.TRUE.equals(request.approved()),
            request.newStaffId(),
            request.reason()
        );
        return success(result, "处理成功");
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录后再访问");
        }
        return authentication.getName();
    }

    private Map<String, Object> success(Object data, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    public record TransferDecisionRequest(Boolean approved, String newStaffId, String reason) {
    }
}