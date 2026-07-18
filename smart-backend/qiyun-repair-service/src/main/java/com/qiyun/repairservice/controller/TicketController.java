package com.qiyun.repairservice.controller;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.repairservice.domain.enums.RepairProcessActionType;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.dto.AiTicketAnalysisViewDto;
import com.qiyun.repairservice.dto.CompletionSummaryDto;
import com.qiyun.repairservice.dto.HistoricalRepairCaseDto;
import com.qiyun.repairservice.dto.RepairProcessRecordDto;
import com.qiyun.repairservice.dto.StaffDashboardDto;
import com.qiyun.repairservice.dto.StaffRecommendationDto;
import com.qiyun.repairservice.dto.TicketDetailDto;
import com.qiyun.repairservice.dto.TicketSummaryDto;
import com.qiyun.repairservice.dto.request.AiTicketAnalysisCorrectionRequest;
import com.qiyun.repairservice.dto.request.RepairProcessRecordRequest;
import com.qiyun.repairservice.dto.request.StudentCompletionRejectRequest;
import com.qiyun.repairservice.dto.request.TicketAssignRequest;
import com.qiyun.repairservice.dto.request.TicketCreateRequest;
import com.qiyun.repairservice.dto.request.TicketRatingRequest;
import com.qiyun.repairservice.dto.request.TicketStatusUpdateRequest;
import com.qiyun.repairservice.service.RepairProcessRecordService;
import com.qiyun.repairservice.service.TicketService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TicketController {

    private final TicketService ticketService;
    private final RepairProcessRecordService repairProcessRecordService;

    // 处理前端发送的 multipart/form-data 报修创建请求
    // 使用 @ModelAttribute 替代 @RequestPart，更宽松地接受 multipart 请求
    @PostMapping(
            value = "/repair-orders",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, "multipart/form-data"},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createWithFiles(
            // 前端虽然会传 studentId，这里统一忽略，始终使用当前登录用户
            @RequestPart(value = "studentId", required = false) String ignoredStudentId,
            @RequestPart(value = "title", required = false) String title,
            @RequestPart(value = "categoryId", required = false) String categoryIdStr,
            @RequestPart(value = "locationText", required = false) String locationText,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "priority", required = false) String priority,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {

        // 始终从 SecurityContext 获取当前登录用户，避免前端伪造 studentId
        String studentId;
        try {
            studentId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (studentId == null || studentId.isBlank()) {
                throw new BusinessException("无法获取当前用户信息，请先登录");
            }
        } catch (Exception e) {
            throw new BusinessException("无法获取当前用户信息，请先登录");
        }

        // 转换 categoryId
        Long categoryId;
        try {
            categoryId = Long.valueOf(categoryIdStr);
        } catch (NumberFormatException e) {
            throw new BusinessException("分类ID格式错误: " + categoryIdStr);
        }

        TicketCreateRequest request = new TicketCreateRequest(
            studentId,
            categoryId,
            locationText,
            description,
            priority,
            null
        );

        TicketDetailDto detail = ticketService.createTicket(request, images);

        // 为了兼容前端统一的 { code, data, message } 响应结构，这里做一层包装
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "报修创建成功");
        result.put("data", detail);
        return result;
    }

    @GetMapping("/repair-orders/my")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> listMyTickets(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        // 从Security Context中获取当前用户ID
        String studentId;
        try {
            studentId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (studentId == null || studentId.isBlank()) {
                throw new BusinessException("无法获取当前用户信息，请先登录");
            }
        } catch (Exception e) {
            throw new BusinessException("无法获取当前用户信息，请先登录");
        }

        // 先获取该学生的所有工单
        List<TicketSummaryDto> allTickets = ticketService.listByStudent(studentId);

        List<TicketSummaryDto> tickets;

        // 根据状态筛选
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            // 将前端状态值映射到后端状态值
            TicketStatus ticketStatus = mapStatusFromFrontend(status);

            // 在该学生的工单中筛选状态
            tickets = allTickets.stream()
                .filter(t -> t.status() == ticketStatus)
                .collect(java.util.stream.Collectors.toList());
        } else {
            // 不筛选状态，返回所有工单
            tickets = allTickets;
        }

        // 简单分页模拟
        int total = tickets.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<TicketSummaryDto> pagedTickets = fromIndex < total ? tickets.subList(fromIndex, toIndex) : new java.util.ArrayList<>();

        // 返回统一格式的响应
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        Map<String, Object> data = new HashMap<>();
        data.put("list", pagedTickets);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", size);
        result.put("data", data);
        return result;
    }

    @GetMapping("/repair-orders/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
    public Map<String, Object> detail(@PathVariable("id") Long id) {
        TicketDetailDto detail = ticketService.getTicketDetail(id, hasRole("ADMIN"));
        assertCanReadTicket(detail);

        // 返回统一格式的响应
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", detail);
        return result;
    }

    @PostMapping("/repair-orders/{id}/completion-summary/regenerate")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public Map<String, Object> regenerateCompletionSummary(@PathVariable("id") Long id) {
        TicketDetailDto detail = ticketService.getTicketDetail(id);
        if (hasRole("STAFF") && !hasRole("ADMIN")) {
            assertStaffOwnsTicket(detail);
        }
        CompletionSummaryDto summary = ticketService.regenerateCompletionSummary(id);
        return success("完成总结已重新生成", summary);
    }

    @GetMapping("/repair-orders/{id}/historical-cases")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public Map<String, Object> recommendHistoricalCases(@PathVariable("id") Long id) {
        TicketDetailDto detail = ticketService.getTicketDetail(id);
        if (hasRole("STAFF") && !hasRole("ADMIN")) {
            assertStaffOwnsTicket(detail);
        }
        List<HistoricalRepairCaseDto> cases = ticketService.recommendHistoricalCases(id);
        return success("相似维修案例获取成功", cases);
    }

    @DeleteMapping("/repair-orders/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> delete(@PathVariable("id") Long id) {
        // 从 SecurityContext 获取当前登录学生ID
        String studentId;
        try {
            studentId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (studentId == null || studentId.isBlank()) {
                throw new BusinessException("无法获取当前用户信息，请先登录");
            }
        } catch (Exception e) {
            throw new BusinessException("无法获取当前用户信息，请先登录");
        }

        ticketService.deleteTicket(id, studentId);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "删除报修单成功");
        result.put("data", null);
        return result;
    }

    @PutMapping("/repair-orders/{id}/confirm-completion")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> confirmCompletion(@PathVariable("id") Long id) {
        TicketDetailDto detail = ticketService.confirmCompletion(id, currentUserId());
        return success("Repair completion confirmed", detail);
    }

    @PutMapping("/repair-orders/{id}/reject-completion")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> rejectCompletion(@PathVariable("id") Long id,
                                                @Valid @RequestBody StudentCompletionRejectRequest request) {
        TicketDetailDto detail = ticketService.rejectCompletion(id, currentUserId(), request.reason());
        return success("Repair returned to processing", detail);
    }

    @PostMapping("/repair-orders/{id}/evaluate")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> evaluate(@PathVariable("id") Long id,
                                        @Valid @RequestBody TicketRatingRequest request) {
        TicketRatingRequest checkedRequest = new TicketRatingRequest(
            currentUserId(),
            request.score(),
            request.comment(),
            request.speedRating(),
            request.qualityRating(),
            request.attitudeRating(),
            request.resolved(),
            request.anonymous()
        );
        TicketDetailDto detail = ticketService.rateTicket(id, checkedRequest);

        // 统一返回结构 { code, data, message }
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "评价成功");
        result.put("data", detail);
        return result;
    }

    @PostMapping("/tasks/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketDetailDto assign(@PathVariable("id") Long id,
                                  @RequestBody TicketAssignRequest request) {
        if (request == null || request.staffId() == null || request.staffId().isBlank()) {
            throw new BusinessException("维修工ID不能为空");
        }
        TicketAssignRequest checkedRequest = new TicketAssignRequest(currentUserId(), request.staffId());
        return ticketService.assignTicket(id, checkedRequest);
    }

    @PutMapping("/tasks/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public Map<String, Object> changeTaskStatus(@PathVariable("id") Long id,
                                          @RequestBody Map<String, Object> requestBody) {
        // 从 SecurityContext 获取当前操作员ID
        String operatorId;
        try {
            operatorId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (operatorId == null || operatorId.isBlank()) {
                throw new BusinessException("无法获取当前用户信息，请先登录");
            }
        } catch (Exception e) {
            throw new BusinessException("无法获取当前用户信息，请先登录");
        }

        // 从请求体中获取状态
        if (hasRole("STAFF") && !hasRole("ADMIN")) {
            assertStaffOwnsTicket(ticketService.getTicketDetail(id));
        }

        String newStatusStr = (String) requestBody.get("newStatus");
        if (newStatusStr == null || newStatusStr.isBlank()) {
            throw new BusinessException("新状态不能为空");
        }

        // 将字符串状态转换为枚举
        TicketStatus newStatus;
        try {
            newStatus = TicketStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException e) {
            // 尝试从前端状态值映射
            newStatus = mapStatusFromFrontend(newStatusStr);
        }

        String rejectionReason = (String) requestBody.get("rejectionReason");

        TicketStatusUpdateRequest request = new TicketStatusUpdateRequest(
            operatorId,
            newStatus,
            rejectionReason
        );

        TicketDetailDto detail = ticketService.updateStatus(id, request);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "任务状态更新成功");
        result.put("data", detail);
        return result;
    }

    @PutMapping("/tasks/{id}/complete")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> completeTask(@PathVariable("id") Long id,
                                      @RequestBody Map<String, Object> requestBody) {
        // 从 SecurityContext 获取当前操作员ID
        String operatorId;
        try {
            operatorId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (operatorId == null || operatorId.isBlank()) {
                throw new BusinessException("无法获取当前用户信息，请先登录");
            }
        } catch (Exception e) {
            throw new BusinessException("无法获取当前用户信息，请先登录");
        }

        // 完成任务时，状态应该是 RESOLVED
        String rejectionReason = (String) requestBody.get("rejectionReason");
        String notes = (String) requestBody.get("notes");

        assertStaffOwnsTicket(ticketService.getTicketDetail(id));

        if (notes != null && !notes.isBlank()) {
            ticketService.updateProcessNotes(id, notes, operatorId);
        }

        TicketStatusUpdateRequest request = new TicketStatusUpdateRequest(
            operatorId,
            TicketStatus.RESOLVED,
            rejectionReason
        );

        TicketDetailDto detail = ticketService.updateStatus(id, request);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "任务完成成功");
        result.put("data", detail);
        return result;
    }

    @PutMapping("/tasks/{id}/arrive")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> arriveTask(@PathVariable("id") Long id,
                                          @RequestBody(required = false) Map<String, Object> requestBody) {
        assertStaffOwnsTicket(ticketService.getTicketDetail(id));
        String content = textValue(requestBody, "content", "维修人员已到达现场并开始核查故障。");
        String imageUrl = textValue(requestBody, "imageUrl", null);
        RepairProcessRecordDto record = repairProcessRecordService.addRecord(
            id,
            currentUserId(),
            new RepairProcessRecordRequest(RepairProcessActionType.ARRIVED, content, imageUrl)
        );
        return success("到场确认已提交", record);
    }

    @PostMapping("/tasks/{id}/process-records")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> addTaskProcessRecord(@PathVariable("id") Long id,
                                                    @Valid @RequestBody RepairProcessRecordRequest request) {
        assertStaffOwnsTicket(ticketService.getTicketDetail(id));
        RepairProcessRecordDto record = repairProcessRecordService.addRecord(id, currentUserId(), request);
        return success("维修过程记录已提交", record);
    }

    @PostMapping(value = "/tasks/{id}/process-records/with-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> addTaskProcessRecordWithImages(@PathVariable("id") Long id,
                                                              @Valid @RequestPart("record") RepairProcessRecordRequest request,
                                                              @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        assertStaffOwnsTicket(ticketService.getTicketDetail(id));
        RepairProcessRecordDto record = repairProcessRecordService.addRecordWithImages(id, currentUserId(), request, images);
        return success("维修过程记录已提交", record);
    }

    @PostMapping("/tasks/{id}/transfer-request")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> requestTransfer(@PathVariable("id") Long id,
                                               @RequestBody Map<String, Object> requestBody) {
        assertStaffOwnsTicket(ticketService.getTicketDetail(id));
        String reason = textValue(requestBody, "reason", "");
        if (reason.isBlank()) {
            reason = textValue(requestBody, "content", "");
        }
        if (reason.isBlank()) {
            throw new BusinessException("请填写转派原因");
        }
        RepairProcessRecordDto record = repairProcessRecordService.addRecord(
            id,
            currentUserId(),
            new RepairProcessRecordRequest(RepairProcessActionType.TRANSFER_REQUEST, reason, textValue(requestBody, "imageUrl", null))
        );
        return success("转派申请已提交", record);
    }

    @GetMapping("/tasks/my")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> listMyTasks(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        // 从Security Context中获取当前维修工ID
        String staffId;
        try {
            staffId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (staffId == null || staffId.isBlank()) {
                throw new BusinessException("无法获取当前用户信息，请先登录");
            }
        } catch (Exception e) {
            throw new BusinessException("无法获取当前用户信息，请先登录");
        }

        // 转换状态参数
        TicketStatus ticketStatus = null;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            ticketStatus = mapStatusFromFrontend(status);
        }

        // 使用分页查询
        return ticketService.listStaffTasksPage(staffId, ticketStatus, page, size);
    }

    /**
     * 获取维修工工作台统计数据
     */
    @GetMapping("/staff/dashboard")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> getStaffDashboard() {
        String staffId = currentUserId();
        StaffDashboardDto dashboard = ticketService.getStaffDashboard(staffId);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取维修工统计数据成功");
        result.put("data", dashboard);
        return result;
    }

    /**
     * 获取维修工任务详情（增强版，包含更多关联数据）
     */
    @GetMapping("/tasks/{id}/detail")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> getTaskDetailWithRecords(@PathVariable("id") Long id) {
        TicketDetailDto detail = ticketService.getTicketDetail(id);
        assertStaffOwnsTicket(detail);

        // 返回统一格式的响应
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", detail);
        return result;
    }

    // ==================== 工单处理流程接口 ====================

    /**
     * 维修工主动接单
     * POST /api/tasks/{id}/accept
     *
     * 状态流转: WAITING_ACCEPT -> IN_PROGRESS
     */
    @PostMapping("/tasks/{id}/accept")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> acceptTask(@PathVariable("id") Long id) {
        String staffId = currentUserId();
        TicketDetailDto detail = ticketService.acceptTicket(id, staffId);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "接单成功，请尽快处理");
        result.put("data", detail);
        return result;
    }

    /**
     * 维修工完成工单
     * PUT /api/tasks/{id}/resolve
     *
     * 状态流转: IN_PROGRESS -> RESOLVED
     */
    @PutMapping("/tasks/{id}/resolve")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> resolveTask(@PathVariable("id") Long id,
                                           @RequestBody(required = false) Map<String, Object> requestBody) {
        String staffId = currentUserId();
        String repairNotes = requestBody != null ? (String) requestBody.get("repairNotes") : null;

        TicketDetailDto detail = ticketService.resolveTicket(id, staffId, repairNotes);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "工单已完成，等待学生确认");
        result.put("data", detail);
        return result;
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> taskDetail(@PathVariable("id") Long id) {
        TicketDetailDto detail = ticketService.getTicketDetail(id);
        assertStaffOwnsTicket(detail);

        // 返回统一格式的响应
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", detail);
        return result;
    }

    @GetMapping("/admin/repair-orders")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> listAllTickets(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted) {

        // 先取出所有工单摘要（根据 includeDeleted 参数决定是否包含已删除的）
        List<TicketSummaryDto> allTickets = ticketService.listAll(includeDeleted);

        // 按照前端传入的条件在内存中过滤（数据量不大时足够）
        java.util.stream.Stream<TicketSummaryDto> stream = allTickets.stream();

        // 1. 状态筛选（前端传的是 pending/processing/...）
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            TicketStatus targetStatus = mapStatusFromFrontend(status);
            stream = stream.filter(t -> t.status() == targetStatus);
        }

        // 2. 分类筛选（前端传的是 categoryName / categoryKey，当前我们用 categoryName 直接对比）
        if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
            stream = stream.filter(t -> category.equalsIgnoreCase(t.categoryName()));
        }

        // 3. 关键词筛选（在描述、位置、工单ID、学生ID、维修工ID、分类名称中模糊匹配）
        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase().trim();
            final String searchKeyword = lower; // 用于lambda表达式
            stream = stream.filter(t -> {
                String desc = t.description() != null ? t.description().toLowerCase() : "";
                String loc = t.locationText() != null ? t.locationText().toLowerCase() : "";
                String ticketIdStr = t.ticketId() != null ? String.valueOf(t.ticketId()) : "";
                String studentIdStr = t.studentId() != null ? t.studentId().toLowerCase() : "";
                String staffIdStr = t.staffId() != null ? t.staffId().toLowerCase() : "";
                String categoryNameStr = t.categoryName() != null ? t.categoryName().toLowerCase() : "";
                boolean matches = desc.contains(searchKeyword)
                    || loc.contains(searchKeyword)
                    || ticketIdStr.contains(searchKeyword)
                    || studentIdStr.contains(searchKeyword)
                    || staffIdStr.contains(searchKeyword)
                    || categoryNameStr.contains(searchKeyword);
                return matches;
            });
        }

        List<TicketSummaryDto> filtered = stream.toList();

        // 简单分页模拟
        int total = filtered.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<TicketSummaryDto> pagedTickets =
                fromIndex < total ? filtered.subList(fromIndex, toIndex) : new java.util.ArrayList<>();

        // 返回统一格式，兼容前端对 { code, data: { list, total, page, pageSize } } 的处理
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        Map<String, Object> data = new HashMap<>();
        data.put("list", pagedTickets);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", size);
        result.put("data", data);
        return result;
    }

    @PutMapping("/admin/repair-orders/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminAssign(@PathVariable("id") Long id,
                                     @Valid @RequestBody Map<String, String> requestBody) {
        // 从 SecurityContext 获取当前操作员ID
        String operatorId;
        try {
            operatorId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (operatorId == null || operatorId.isBlank()) {
                throw new BusinessException("无法获取当前用户信息，请先登录");
            }
        } catch (Exception e) {
            throw new BusinessException("无法获取当前用户信息，请先登录");
        }

        // 从请求体中获取维修工ID
        String staffId = requestBody.get("repairmanId") != null ? requestBody.get("repairmanId") : requestBody.get("staffId");
        if (staffId == null || staffId.isBlank()) {
            throw new BusinessException("维修工ID不能为空");
        }

        // 构造分配请求
        TicketAssignRequest request = new TicketAssignRequest(operatorId, staffId);
        TicketDetailDto detail = ticketService.assignTicket(id, request);

        // 返回统一格式
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "工单分配成功");
        result.put("data", detail);
        return result;
    }

    @GetMapping("/admin/repair-orders/{id}/recommend-staff")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> recommendStaff(@PathVariable("id") Long id) {
        List<StaffRecommendationDto> recommendations = ticketService.recommendStaffForTicket(id);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "维修人员推荐成功");
        result.put("data", recommendations);
        return result;
    }

    @PutMapping("/admin/repair-orders/{id}/ai-analysis/correction")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> correctAiAnalysis(@PathVariable("id") Long id,
                                                 @Valid @RequestBody AiTicketAnalysisCorrectionRequest request) {
        AiTicketAnalysisViewDto analysis = ticketService.correctAiAnalysis(id, request, currentUserId());
        return success("AI analysis correction saved", analysis);
    }

    @PostMapping("/admin/repair-orders/history-cases/rebuild-index")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> rebuildHistoricalCaseIndex() {
        int count = ticketService.rebuildHistoricalCaseIndex(currentUserId());
        return success("历史维修案例索引重建完成", Map.of("syncedCount", count));
    }

    @PutMapping("/admin/repair-orders/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketDetailDto adminChangeStatus(@PathVariable("id") Long id,
                                           @RequestBody TicketStatusUpdateRequest request) {
        if (request == null || request.newStatus() == null) {
            throw new BusinessException("请选择新的工单状态");
        }
        TicketStatusUpdateRequest checkedRequest = new TicketStatusUpdateRequest(
            currentUserId(),
            request.newStatus(),
            request.rejectionReason()
        );
        return ticketService.updateStatus(id, checkedRequest);
    }

    @PutMapping("/admin/repair-orders/{id}/repair-notes")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketDetailDto updateRepairNotes(@PathVariable("id") Long id,
                                           @RequestBody UpdateNotesRequest request) {
        String operatorId = currentUserId();
        return ticketService.updateRepairNotes(id, request.notes(), operatorId);
    }

    @PutMapping("/admin/repair-orders/{id}/process-notes")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketDetailDto updateProcessNotes(@PathVariable("id") Long id,
                                             @RequestBody UpdateNotesRequest request) {
        String operatorId = currentUserId();
        return ticketService.updateProcessNotes(id, request.notes(), operatorId);
    }

    @PutMapping("/admin/repair-orders/{id}/estimated-completion-time")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketDetailDto setEstimatedCompletionTime(@PathVariable("id") Long id,
                                                    @RequestBody SetEstimatedTimeRequest request) {
        String operatorId = currentUserId();
        return ticketService.setEstimatedCompletionTime(id, request.estimatedTime(), operatorId);
    }

    // 将前端状态值映射到后端枚举值
    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录后再访问");
        }
        return authentication.getName();
    }

    private boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String authority = "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
    }

    private void assertCanReadTicket(TicketDetailDto detail) {
        if (hasRole("ADMIN")) {
            return;
        }

        String userId = currentUserId();
        if (hasRole("STUDENT") && userId.equals(detail.studentId())) {
            return;
        }
        if (hasRole("STAFF") && userId.equals(detail.staffId())) {
            return;
        }

        throw new BusinessException(HttpStatus.FORBIDDEN, "当前账号无权查看该工单");
    }

    private void assertStaffOwnsTicket(TicketDetailDto detail) {
        if (!currentUserId().equals(detail.staffId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "当前账号无权查看该维修任务");
        }
    }

    private TicketStatus mapStatusFromFrontend(String frontendStatus) {
        String status = frontendStatus.toLowerCase();
        return switch (status) {
            case "pending" -> TicketStatus.WAITING_ACCEPT;
            case "processing" -> TicketStatus.IN_PROGRESS;
            case "completed", "resolved" -> TicketStatus.RESOLVED;
            case "to_be_evaluated", "waiting_feedback" -> TicketStatus.WAITING_FEEDBACK;
            case "closed" -> TicketStatus.CLOSED;
            case "rejected" -> TicketStatus.REJECTED;
            default -> {
                // 尝试直接转换为枚举值
                try {
                    yield TicketStatus.valueOf(frontendStatus.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new BusinessException("无效的任务状态: " + frontendStatus);
                }
            }
        };
    }

    private Map<String, Object> success(String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    private String textValue(Map<String, Object> body, String key, String fallback) {
        if (body == null || body.get(key) == null) {
            return fallback == null ? "" : fallback;
        }
        return String.valueOf(body.get(key)).trim();
    }

    // 内部请求类
    public record UpdateNotesRequest(String notes) {}

    public record SetEstimatedTimeRequest(java.time.LocalDateTime estimatedTime) {}
}
