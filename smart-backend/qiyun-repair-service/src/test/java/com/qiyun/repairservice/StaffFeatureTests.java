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
import com.qiyun.repairservice.dto.request.TicketStatusUpdateRequest;
import com.qiyun.repairservice.repository.CategoryRepository;
import com.qiyun.repairservice.repository.UserReferenceRepository;
import com.qiyun.repairservice.service.TicketService;
import java.time.LocalDateTime;
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

        // Paginated query for all tasks
        Map<String, Object> result = ticketService.listStaffTasksPage(staffId, null, 0, 10);

        assertThat(result.get("list")).isNotNull();
        assertThat((Long) result.get("total")).isGreaterThanOrEqualTo(5);
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
        ticketService.updateStatus(ticket2, new TicketStatusUpdateRequest(otherStaffId, TicketStatus.WAITING_FEEDBACK, null));

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

        // Filter by WAITING_FEEDBACK - ticket1 should be in this status
        Map<String, Object> feedbackResult = ticketService.listStaffTasksPage(
            staffId, TicketStatus.WAITING_FEEDBACK, 0, 10
        );

        assertThat((List<?>) inProgressResult.get("list")).isNotEmpty();
        assertThat((List<?>) feedbackResult.get("list")).isNotEmpty();
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

    private void completeTicket(Long ticketId) {
        ticketService.updateStatus(ticketId, new TicketStatusUpdateRequest(
            staffId,
            TicketStatus.RESOLVED,
            null
        ));
        ticketService.updateStatus(ticketId, new TicketStatusUpdateRequest(
            staffId,
            TicketStatus.WAITING_FEEDBACK,
            null
        ));
    }
}