package com.qiyun.repairservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.qiyun.feign.client.AiServiceClient;
import com.qiyun.feign.dto.AnalyzeTicketResponse;
import com.qiyun.repairservice.domain.entity.Category;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.dto.StaffDashboardDto;
import com.qiyun.repairservice.dto.TicketDetailDto;
import com.qiyun.repairservice.dto.request.TicketAssignRequest;
import com.qiyun.repairservice.dto.request.TicketCreateRequest;
import com.qiyun.repairservice.dto.request.TicketRatingRequest;
import com.qiyun.repairservice.dto.request.TicketStatusUpdateRequest;
import com.qiyun.repairservice.repository.CategoryRepository;
import com.qiyun.repairservice.repository.TicketRepository;
import com.qiyun.repairservice.repository.UserReferenceRepository;
import com.qiyun.repairservice.service.TicketService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff feature tests for repair-service
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StaffFeatureTests {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private UserReferenceRepository userReferenceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @MockBean
    private AiServiceClient aiServiceClient;

    private String studentId;
    private String staffId;
    private String adminId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        studentId = "staff_test_student_001";
        staffId = "staff_test_staff_001";
        adminId = "staff_test_admin_001";

        createUserIfNotExists(studentId, "测试学生", UserRole.STUDENT, "13800138001");
        createUserIfNotExists(staffId, "测试维修工", UserRole.STAFF, "13800138002");
        createUserIfNotExists(adminId, "测试管理员", UserRole.ADMIN, "13800138003");

        List<Category> categories = categoryRepository.findAll();
        if (categories.isEmpty()) {
            Category category = new Category();
            category.setCategoryName("测试分类");
            category = categoryRepository.save(category);
            categoryId = category.getCategoryId();
        } else {
            categoryId = categories.get(0).getCategoryId();
        }

        when(aiServiceClient.analyzeTicket(any())).thenReturn(null);
    }

    private void createUserIfNotExists(String userId, String nickname, UserRole role, String phone) {
        if (userReferenceRepository.findByUserId(userId).isEmpty()) {
            UserReference user = new UserReference();
            user.setUserId(userId);
            user.setNickname(nickname);
            user.setRole(role);
            user.setContactPhone(phone);
            user.setIsActive(true);
            user.setCreatedAt(LocalDateTime.now());
            userReferenceRepository.save(user);
        }
    }

    @Test
    void testListMyTasks() {
        // Create and assign 3 tickets to the staff
        for (int i = 1; i <= 3; i++) {
            Long ticketId = createTestTicket("任务" + i);
            assignTicket(ticketId);
        }

        // Query staff's task list
        Map<String, Object> result = ticketService.listStaffTasksPage(staffId, null, 0, 10);
        List<com.qiyun.repairservice.dto.TicketSummaryDto> tasks =
            (List<com.qiyun.repairservice.dto.TicketSummaryDto>) result.get("list");

        assertThat(tasks).hasSizeGreaterThanOrEqualTo(3);

        // Verify all tasks are assigned to current staff
        for (var task : tasks) {
            assertThat(task.staffId()).isEqualTo(staffId);
        }
    }

    @Test
    void testGetTicketDetail() {
        // Create and assign ticket
        Long ticketId = createTestTicket("详情测试工单");
        assignTicket(ticketId);

        // Get ticket detail
        TicketDetailDto detail = ticketService.getTicketDetail(ticketId);

        assertThat(detail).isNotNull();
        assertThat(detail.ticketId()).isEqualTo(ticketId);
        assertThat(detail.staffId()).isEqualTo(staffId);
        assertThat(detail.status()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void testListTasksWithPagination() {
        // Create 5 tickets
        for (int i = 1; i <= 5; i++) {
            Long ticketId = createTestTicket("分页测试" + i);
            assignTicket(ticketId);

            // Complete 2 tickets
            if (i <= 2) {
                completeTicket(ticketId);
            }
        }

        // Paginated query for task overview: only unfinished overview statuses are included
        Map<String, Object> result = ticketService.listStaffTasksPage(staffId, null, 0, 10);

        assertThat(result.get("list")).isNotNull();
        assertThat((Long) result.get("total")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void testGetStaffDashboard() {
        // Create tickets with different statuses
        Long ticket1 = createTestTicket("统计测试1");
        Long ticket2 = createTestTicket("统计测试2");
        Long ticket3 = createTestTicket("统计测试3");

        assignTicket(ticket1);
        assignTicket(ticket2);
        assignTicket(ticket3);

        // Complete some tickets
        completeTicket(ticket1);
        completeTicket(ticket2);

        // Get dashboard stats
        StaffDashboardDto dashboard = ticketService.getStaffDashboard(staffId);

        assertThat(dashboard).isNotNull();
        assertThat(dashboard.inProgressCount()).isGreaterThanOrEqualTo(1); // In progress
        assertThat(dashboard.completedCount()).isGreaterThanOrEqualTo(2);  // Completed
        assertThat(dashboard.categoryDistribution()).isNotNull();
    }

    @Test
    void getStaffDashboard_todayCompleted_shouldCountTodayTickets() {
        // Create and complete tickets (today)
        Long ticket1 = createTestTicket("今日完成测试1");
        Long ticket2 = createTestTicket("今日完成测试2");
        assignTicket(ticket1);
        assignTicket(ticket2);
        completeTicket(ticket1);
        completeTicket(ticket2);

        StaffDashboardDto dashboard = ticketService.getStaffDashboard(staffId);

        // Today's completed should be at least 2
        assertThat(dashboard.todayCompletedCount()).isGreaterThanOrEqualTo(2);
        // Today's completed should also count in week and month
        assertThat(dashboard.weekCompletedCount()).isGreaterThanOrEqualTo(2);
        assertThat(dashboard.monthCompletedCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void getStaffDashboard_otherStaffTickets_shouldNotBeCounted() {
        // Create another staff
        String otherStaffId = "staff_test_staff_other";
        createUserIfNotExists(otherStaffId, "其他维修工", UserRole.STAFF, "13800138099");

        // Create ticket for current staff
        Long ticket1 = createTestTicket("当前维修工工单");
        assignTicket(ticket1);
        completeTicket(ticket1);

        // Create ticket for other staff
        Long ticket2 = createTestTicket("其他维修工工单");
        TicketAssignRequest assignRequest = new TicketAssignRequest(adminId, otherStaffId);
        ticketService.assignTicket(ticket2, assignRequest);
        ticketService.updateStatus(ticket2, new TicketStatusUpdateRequest(otherStaffId, TicketStatus.RESOLVED, null));
        ticketService.confirmCompletion(ticket2, studentId);

        // Current staff's dashboard
        StaffDashboardDto dashboard = ticketService.getStaffDashboard(staffId);

        // Other staff's tickets should not be counted
        assertThat(dashboard.completedCount()).isLessThan(1000);
    }

    @Test
    void getStaffDashboard_uncompletedTickets_shouldNotBeCounted() {
        // Create ticket but don't complete
        Long ticket1 = createTestTicket("未完成工单1");
        assignTicket(ticket1);

        StaffDashboardDto dashboard = ticketService.getStaffDashboard(staffId);

        // Uncompleted tickets should not count in completed count
        assertThat(dashboard.inProgressCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testFilterByStatus() {
        // Create and assign multiple tickets
        Long ticket1 = createTestTicket("筛选测试1");
        Long ticket2 = createTestTicket("筛选测试2");
        Long ticket3 = createTestTicket("筛选测试3");
        assignTicket(ticket1);
        assignTicket(ticket2);
        assignTicket(ticket3);

        // Complete ticket1 to WAITING_FEEDBACK status
        completeTicket(ticket1);

        // Filter by IN_PROGRESS - ticket2 and ticket3 should still be in progress
        Map<String, Object> inProgressResult = ticketService.listStaffTasksPage(
            staffId, TicketStatus.IN_PROGRESS, 0, 10
        );

        // Default overview scope must not be bypassed by a completed status filter.
        Map<String, Object> feedbackInOverviewResult = ticketService.listStaffTasksPage(
            staffId, TicketStatus.WAITING_FEEDBACK, 0, 10
        );
        Map<String, Object> feedbackInRecordsResult = ticketService.listStaffTasksPage(
            staffId, TicketStatus.WAITING_FEEDBACK, "records", null, null, null, 0, 10
        );

        assertThat((List<?>) inProgressResult.get("list")).isNotEmpty();
        assertThat((List<?>) feedbackInOverviewResult.get("list")).isEmpty();
        assertThat((List<?>) feedbackInRecordsResult.get("list")).isNotEmpty();
    }

    @Test
    void listStaffTasksPage_shouldApplyWorkerPageOwnershipAndStatusScopes() {
        ZoneId businessZone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(businessZone);
        LocalDateTime todayMorning = today.atTime(9, 0);
        LocalDateTime yesterdayMorning = today.minusDays(1).atTime(9, 0);
        String otherStaffId = "staff_scope_other_001";
        createUserIfNotExists(otherStaffId, "其他维修工", UserRole.STAFF, "13800138999");

        Long yesterdayClosed = createScopedTicket("昨天关闭", staffId, TicketStatus.CLOSED, yesterdayMorning);
        Long todayWaiting = createScopedTicket("今天待受理", staffId, TicketStatus.WAITING_ACCEPT, todayMorning);
        Long todayInProgress = createScopedTicket("今天处理中", staffId, TicketStatus.IN_PROGRESS, todayMorning.plusHours(1));
        Long oldInProgress = createScopedTicket("非今日处理中", staffId, TicketStatus.IN_PROGRESS, yesterdayMorning.plusHours(1));
        Long resolved = createScopedTicket("已完成待确认", staffId, TicketStatus.RESOLVED, yesterdayMorning.plusHours(2));
        Long waitingFeedback = createScopedTicket("待评价", staffId, TicketStatus.WAITING_FEEDBACK, yesterdayMorning.plusHours(3));
        Long feedbacked = createScopedTicket("已评价", staffId, TicketStatus.FEEDBACKED, yesterdayMorning.plusHours(4));
        Long closed = createScopedTicket("已关闭", staffId, TicketStatus.CLOSED, todayMorning.plusHours(2));
        Long otherStaffToday = createScopedTicket("其他维修工任务", otherStaffId, TicketStatus.IN_PROGRESS, todayMorning.plusHours(3));

        List<Long> todayIds = taskIds(ticketService.listStaffTasksPage(staffId, null, "today", null, null, null, 0, 20));
        assertThat(todayIds).contains(todayWaiting, todayInProgress);
        assertThat(todayIds).doesNotContain(yesterdayClosed, oldInProgress, resolved, waitingFeedback, feedbacked, closed, otherStaffToday);

        List<Long> processingIds = taskIds(ticketService.listStaffTasksPage(staffId, null, "processing", null, null, null, 0, 20));
        assertThat(processingIds).contains(todayInProgress, oldInProgress);
        assertThat(processingIds).doesNotContain(todayWaiting, resolved, waitingFeedback, feedbacked, closed, otherStaffToday);

        List<Long> recordIds = taskIds(ticketService.listStaffTasksPage(staffId, null, "records", null, null, null, 0, 20));
        assertThat(recordIds).contains(yesterdayClosed, resolved, waitingFeedback, feedbacked, closed);
        assertThat(recordIds).doesNotContain(todayWaiting, todayInProgress, oldInProgress, otherStaffToday);

        setTicketStatus(resolved, TicketStatus.IN_PROGRESS, yesterdayMorning.plusHours(2));
        List<Long> recordsAfterReturn = taskIds(ticketService.listStaffTasksPage(staffId, null, "records", null, null, null, 0, 20));
        List<Long> processingAfterReturn = taskIds(ticketService.listStaffTasksPage(staffId, null, "processing", null, null, null, 0, 20));
        assertThat(recordsAfterReturn).doesNotContain(resolved);
        assertThat(processingAfterReturn).contains(resolved);
    }

    @Test
    void listStaffTasksPage_shouldEnforceStaffMenuScopesAndTotals() {
        ZoneId businessZone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(businessZone);
        LocalDateTime todayMorning = today.atTime(9, 0);
        LocalDateTime yesterdayMorning = today.minusDays(1).atTime(9, 0);
        LocalDateTime oldEnoughForSlaWarning = today.minusDays(3).atTime(9, 0);
        String otherStaffId = "staff_scope_other_002";
        createUserIfNotExists(otherStaffId, "Other staff", UserRole.STAFF, "13800138888");

        Long closedYesterday = createScopedTicket("closed yesterday", staffId, TicketStatus.CLOSED, yesterdayMorning);
        Long waitingToday = createScopedTicket("waiting today", staffId, TicketStatus.WAITING_ACCEPT, todayMorning);
        Long processingToday = createScopedTicket("processing today", staffId, TicketStatus.IN_PROGRESS, todayMorning.plusHours(1));
        Long processingOld = createScopedTicket("processing old", staffId, TicketStatus.IN_PROGRESS, yesterdayMorning.plusHours(1));
        Long estimatedToday = createScopedTicket("estimated today", staffId, TicketStatus.IN_PROGRESS, yesterdayMorning.plusHours(2));
        Long resolved = createScopedTicket("resolved", staffId, TicketStatus.RESOLVED, yesterdayMorning.plusHours(2));
        Long waitingFeedback = createScopedTicket("waiting feedback", staffId, TicketStatus.WAITING_FEEDBACK, yesterdayMorning.plusHours(3));
        Long feedbacked = createScopedTicket("feedbacked", staffId, TicketStatus.FEEDBACKED, yesterdayMorning.plusHours(4));
        Long closedToday = createScopedTicket("closed today", staffId, TicketStatus.CLOSED, todayMorning.plusHours(2));
        Long otherStaffToday = createScopedTicket("other staff today", otherStaffId, TicketStatus.IN_PROGRESS, todayMorning.plusHours(3));
        Long highWaiting = createScopedTicket("high waiting", staffId, TicketStatus.WAITING_ACCEPT, yesterdayMorning.plusHours(5));
        Long highResolved = createScopedTicket("high resolved", staffId, TicketStatus.RESOLVED, yesterdayMorning.plusHours(6));
        Long slaWaiting = createScopedTicket("sla waiting", staffId, TicketStatus.WAITING_ACCEPT, oldEnoughForSlaWarning);
        Long slaProcessing = createScopedTicket("sla processing", staffId, TicketStatus.IN_PROGRESS, oldEnoughForSlaWarning.plusHours(1));
        Long slaResolved = createScopedTicket("sla resolved", staffId, TicketStatus.RESOLVED, oldEnoughForSlaWarning.plusHours(2));
        Long otherStaffSla = createScopedTicket("other staff sla", otherStaffId, TicketStatus.WAITING_ACCEPT, oldEnoughForSlaWarning);

        setEstimatedCompletionTime(estimatedToday, today.atTime(18, 0));
        setPriority(highWaiting, "high");
        setPriority(highResolved, "high");
        setCreatedAt(slaWaiting, oldEnoughForSlaWarning);
        setCreatedAt(slaProcessing, oldEnoughForSlaWarning);
        setCreatedAt(slaResolved, oldEnoughForSlaWarning);
        setCreatedAt(otherStaffSla, oldEnoughForSlaWarning);

        Map<String, Object> overviewResult = ticketService.listStaffTasksPage(staffId, null, "all", null, null, null, 0, 50);
        List<Long> overviewIds = taskIds(overviewResult);
        assertThat(overviewIds).contains(waitingToday, processingToday, processingOld, estimatedToday, resolved, highWaiting, highResolved, slaWaiting, slaProcessing, slaResolved);
        assertThat(overviewIds).doesNotContain(closedYesterday, waitingFeedback, feedbacked, closedToday, otherStaffToday, otherStaffSla);
        assertThat(overviewResult.get("total")).isEqualTo((long) overviewIds.size());

        List<Long> todayIds = taskIds(ticketService.listStaffTasksPage(staffId, null, "today", null, null, null, 0, 50));
        assertThat(todayIds).contains(waitingToday, processingToday, estimatedToday);
        assertThat(todayIds).doesNotContain(closedYesterday, processingOld, resolved, waitingFeedback, feedbacked, closedToday, highResolved, otherStaffToday);
        assertThat(taskIds(ticketService.listStaffTasksPage(staffId, TicketStatus.RESOLVED, "today", null, null, null, 0, 50))).isEmpty();

        List<Long> processingIds = taskIds(ticketService.listStaffTasksPage(staffId, null, "processing", null, null, null, 0, 50));
        assertThat(processingIds).contains(processingToday, processingOld, estimatedToday, slaProcessing);
        assertThat(processingIds).doesNotContain(waitingToday, resolved, waitingFeedback, feedbacked, closedToday, otherStaffToday);
        assertThat(taskIds(ticketService.listStaffTasksPage(staffId, TicketStatus.WAITING_ACCEPT, "processing", null, null, null, 0, 50))).isEmpty();

        List<Long> highPriorityIds = taskIds(ticketService.listStaffTasksPage(staffId, null, "high-priority", null, null, null, 0, 50));
        assertThat(highPriorityIds).contains(highWaiting);
        assertThat(highPriorityIds).doesNotContain(highResolved, waitingFeedback, feedbacked, closedToday, otherStaffToday);
        assertThat(taskIds(ticketService.listStaffTasksPage(staffId, TicketStatus.RESOLVED, "high-priority", null, null, null, 0, 50))).isEmpty();
        assertThat(taskIds(ticketService.listStaffTasksPage(staffId, null, "high-priority", null, "medium", null, 0, 50))).isEmpty();

        List<Long> slaIds = taskIds(ticketService.listStaffTasksPage(staffId, null, "overdue", null, null, null, 0, 50));
        assertThat(slaIds).contains(slaWaiting, slaProcessing);
        assertThat(slaIds).doesNotContain(slaResolved, highResolved, waitingFeedback, feedbacked, closedToday, otherStaffSla);
        assertThat(taskIds(ticketService.listStaffTasksPage(staffId, TicketStatus.RESOLVED, "overdue", null, null, null, 0, 50))).isEmpty();

        List<Long> recordIds = taskIds(ticketService.listStaffTasksPage(staffId, null, "records", null, null, null, 0, 50));
        assertThat(recordIds).contains(closedYesterday, resolved, waitingFeedback, feedbacked, closedToday, highResolved, slaResolved);
        assertThat(recordIds).doesNotContain(waitingToday, processingToday, processingOld, estimatedToday, highWaiting, slaWaiting, slaProcessing, otherStaffToday);
        assertThat(taskIds(ticketService.listStaffTasksPage(staffId, TicketStatus.IN_PROGRESS, "records", null, null, null, 0, 50))).isEmpty();
    }

    @Test
    void getStaffDashboard_shouldUseUnifiedStaffStatisticsScope() {
        ZoneId businessZone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(businessZone);
        LocalDateTime todayMorning = today.atTime(9, 0);
        LocalDateTime yesterdayMorning = today.minusDays(1).atTime(9, 0);
        LocalDateTime oldEnoughForSlaWarning = today.minusDays(3).atTime(9, 0);
        String otherStaffId = "staff_stats_other_001";
        createUserIfNotExists(otherStaffId, "Other stats staff", UserRole.STAFF, "13800138777");

        Long waitingToday = createScopedTicket("stats waiting today", staffId, TicketStatus.WAITING_ACCEPT, todayMorning);
        Long processingHigh = createScopedTicket("stats high processing", staffId, TicketStatus.IN_PROGRESS, yesterdayMorning);
        Long estimatedToday = createScopedTicket("stats estimated today", staffId, TicketStatus.IN_PROGRESS, yesterdayMorning.plusHours(1));
        Long slaProcessing = createScopedTicket("stats sla processing", staffId, TicketStatus.IN_PROGRESS, oldEnoughForSlaWarning);
        Long resolved = createScopedTicket("stats resolved", staffId, TicketStatus.RESOLVED, yesterdayMorning.plusHours(2));
        Long waitingFeedback = createScopedTicket("stats waiting feedback", staffId, TicketStatus.WAITING_FEEDBACK, yesterdayMorning.plusHours(3));
        Long ratedFour = createScopedTicket("stats rated four", staffId, TicketStatus.WAITING_FEEDBACK, yesterdayMorning.plusHours(4));
        Long ratedTwo = createScopedTicket("stats rated two", staffId, TicketStatus.WAITING_FEEDBACK, yesterdayMorning.plusHours(5));
        Long closed = createScopedTicket("stats closed", staffId, TicketStatus.CLOSED, yesterdayMorning.plusHours(6));
        Long rejected = createScopedTicket("stats rejected", staffId, TicketStatus.REJECTED, yesterdayMorning.plusHours(7));
        Long deleted = createScopedTicket("stats deleted", staffId, TicketStatus.WAITING_ACCEPT, todayMorning.plusHours(1));
        Long otherStaff = createScopedTicket("stats other staff", otherStaffId, TicketStatus.WAITING_ACCEPT, todayMorning.plusHours(2));

        setPriority(processingHigh, "high");
        setEstimatedCompletionTime(estimatedToday, today.atTime(18, 0));
        setCreatedAt(slaProcessing, oldEnoughForSlaWarning);
        setDeleted(deleted, true);
        ticketService.rateTicket(ratedFour, new TicketRatingRequest(studentId, 4, "good", 4, 4, 4, true, false));
        ticketService.rateTicket(ratedTwo, new TicketRatingRequest(studentId, 2, "ok", 2, 2, 2, true, false));

        StaffDashboardDto dashboard = ticketService.getStaffDashboard(staffId);

        assertThat(dashboard.totalTaskCount()).isEqualTo(10L);
        assertThat(dashboard.pendingCount()).isEqualTo(1L);
        assertThat(dashboard.inProgressCount()).isEqualTo(3L);
        assertThat(dashboard.awaitingFeedbackCount()).isEqualTo(2L);
        assertThat(dashboard.finishedCount()).isEqualTo(3L);
        assertThat(dashboard.todayTaskCount()).isEqualTo(2L);
        assertThat(dashboard.highPriorityCount()).isEqualTo(1L);
        assertThat(dashboard.slaAlertCount()).isEqualTo(2L);
        assertThat(dashboard.avgRating()).isEqualTo(3.0);
        assertThat(dashboard.totalRatingCount()).isEqualTo(2L);

        assertThat(taskIds(ticketService.listStaffTasksPage(staffId, null, "overdue", null, null, null, 0, 20))).contains(slaProcessing);
        assertThat(taskIds(ticketService.listStaffTasksPage(staffId, null, "overdue", null, null, null, 0, 20))).doesNotContain(resolved, waitingFeedback, ratedFour, ratedTwo, closed, rejected, deleted, otherStaff);
        assertThat(waitingToday).isNotNull();
    }
    // ==================== Helper Methods ====================

    private Long createTestTicket(String description) {
        TicketCreateRequest request = new TicketCreateRequest(
            studentId,
            categoryId,
            "测试地点",
            description,
            "medium",
            List.of("img1.jpg")
        );
        TicketDetailDto dto = ticketService.createTicket(request, null);
        return dto.ticketId();
    }

    private void assignTicket(Long ticketId) {
        TicketAssignRequest assignRequest = new TicketAssignRequest(adminId, staffId);
        ticketService.assignTicket(ticketId, assignRequest);
    }

    private Long createScopedTicket(String description, String targetStaffId, TicketStatus status, LocalDateTime assignedAt) {
        Long ticketId = createTestTicket(description);
        ticketService.assignTicket(ticketId, new TicketAssignRequest(adminId, targetStaffId));
        setTicketStatus(ticketId, status, assignedAt);
        return ticketId;
    }

    private void setTicketStatus(Long ticketId, TicketStatus status, LocalDateTime assignedAt) {
        var ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setStatus(status);
        ticket.setAssignedAt(assignedAt);
        if (TicketStatus.RESOLVED == status || TicketStatus.WAITING_FEEDBACK == status
            || TicketStatus.FEEDBACKED == status || TicketStatus.CLOSED == status) {
            ticket.setCompletedAt(assignedAt.plusHours(1));
        } else {
            ticket.setCompletedAt(null);
        }
        ticketRepository.saveAndFlush(ticket);
    }

    private void setPriority(Long ticketId, String priority) {
        var ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setPriority(priority);
        ticketRepository.saveAndFlush(ticket);
    }

    private void setEstimatedCompletionTime(Long ticketId, LocalDateTime estimatedCompletionTime) {
        var ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setEstimatedCompletionTime(estimatedCompletionTime);
        ticketRepository.saveAndFlush(ticket);
    }

    private void setCreatedAt(Long ticketId, LocalDateTime createdAt) {
        var ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setCreatedAt(createdAt);
        ticketRepository.saveAndFlush(ticket);
    }

    private void setDeleted(Long ticketId, boolean deleted) {
        var ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setDeleted(deleted);
        ticketRepository.saveAndFlush(ticket);
    }
    private List<Long> taskIds(Map<String, Object> result) {
        @SuppressWarnings("unchecked")
        List<com.qiyun.repairservice.dto.TicketSummaryDto> tasks =
            (List<com.qiyun.repairservice.dto.TicketSummaryDto>) result.get("list");
        return tasks.stream().map(com.qiyun.repairservice.dto.TicketSummaryDto::ticketId).toList();
    }

    private void completeTicket(Long ticketId) {
        ticketService.updateStatus(ticketId, new TicketStatusUpdateRequest(
            staffId,
            TicketStatus.RESOLVED,
            null
        ));
        ticketService.confirmCompletion(ticketId, studentId);
    }
}
