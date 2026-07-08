package com.ligong.reportingcenter.controller;

import com.ligong.reportingcenter.dto.RepairProcessRecordDto;
import com.ligong.reportingcenter.dto.request.RepairProcessRecordRequest;
import com.ligong.reportingcenter.exception.BusinessException;
import com.ligong.reportingcenter.service.RepairProcessRecordService;
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
@RequestMapping("/api/repair-orders/{ticketId}/process-records")
public class RepairProcessRecordController {

    private final RepairProcessRecordService repairProcessRecordService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
    public Map<String, Object> list(@PathVariable("ticketId") Long ticketId) {
        List<RepairProcessRecordDto> records = repairProcessRecordService.listRecords(ticketId, currentUserId());
        return success("维修过程记录获取成功", records);
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> add(@PathVariable("ticketId") Long ticketId,
                                   @Valid @RequestBody RepairProcessRecordRequest request) {
        RepairProcessRecordDto record = repairProcessRecordService.addRecord(ticketId, currentUserId(), request);
        return success("维修过程记录已提交", record);
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
