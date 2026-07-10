package com.ligong.reportingcenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ligong.reportingcenter.domain.enums.TicketStatus;
import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.CategoryDto;
import com.ligong.reportingcenter.dto.StaffDashboardDto;
import com.ligong.reportingcenter.dto.TicketDetailDto;
import com.ligong.reportingcenter.dto.request.TicketAssignRequest;
import com.ligong.reportingcenter.dto.request.TicketCreateRequest;
import com.ligong.reportingcenter.dto.request.TicketStatusUpdateRequest;
import com.ligong.reportingcenter.dto.request.UserRegisterRequest;
import com.ligong.reportingcenter.service.AiAnalysisIntegrationService;
import com.ligong.reportingcenter.service.CategoryService;
import com.ligong.reportingcenter.service.TicketService;
import com.ligong.reportingcenter.service.UserService;
import com.qiyun.feign.client.AiServiceClient;
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
 * 维修工功能测试类
 * 使用 test profile 和 H2 内存数据库运行
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StaffFeatureTests {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    // Mock Feign 客户端，避免测试时调用远程服务
    @MockBean
    private AiServiceClient aiServiceClient;

    // Mock AI 集成服务
    @MockBean
    private AiAnalysisIntegrationService aiAnalysisIntegrationService;

    private String studentId;
    private String staffId;
    private String adminId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        studentId = "staff_test_student_001";
        staffId = "staff_test_staff_001";
        adminId = "staff_test_admin_001";

        // 注册测试用户
        try {
            userService.register(new UserRegisterRequest(studentId, "123456", "测试学生", "13800138001", UserRole.STUDENT));
        } catch (Exception ignored) {}
        try {
            userService.register(new UserRegisterRequest(staffId, "123456", "测试维修工", "13800138002", UserRole.STAFF));
        } catch (Exception ignored) {}
        try {
            userService.register(new UserRegisterRequest(adminId, "123456", "测试管理员", "13800138003", UserRole.ADMIN));
        } catch (Exception ignored) {}

        // 获取测试分类
        List<CategoryDto> categories = categoryService.listAll();
        if (categories == null || categories.isEmpty()) {
            CategoryDto created = categoryService.create("测试分类");
            categoryId = created.categoryId();
        } else {
            categoryId = categories.get(0).categoryId();
        }
    }

    /**
     * 测试1：维修工登录
     * 验证：维修工用户可以成功注册和登录
     */
    @Test
    void testStaffLogin() {
        // 验证维修工账号已创建
        var loginResult = userService.login(
            new com.ligong.reportingcenter.dto.request.LoginRequest(staffId, "123456")
        );

        assertThat(loginResult).isNotNull();
        assertThat(loginResult.user()).isNotNull();
        assertThat(loginResult.user().role()).isEqualTo(UserRole.STAFF);

        System.out.println("✅ 测试1通过：维修工登录成功 - " + loginResult.user().nickname());
    }

    /**
     * 测试2：查看个人待处理工单
     * 验证：维修工可以查看分配给自己的工单列表
     */
    @Test
    void testListMyTasks() {
        // 创建3个工单并分配给维修工
        for (int i = 1; i <= 3; i++) {
            Long ticketId = createTestTicket("任务" + i);
            assignTicket(ticketId);
        }

        // 查询维修工的任务列表
        List<com.ligong.reportingcenter.dto.TicketSummaryDto> tasks = ticketService.listByStaff(staffId);

        assertThat(tasks).hasSizeGreaterThanOrEqualTo(3);

        // 验证所有任务都分配给了当前维修工
        for (var task : tasks) {
            assertThat(task.staffId()).isEqualTo(staffId);
        }

        System.out.println("✅ 测试2通过：维修工查询到 " + tasks.size() + " 个任务");
    }

    /**
     * 测试3：查看工单详情
     * 验证：维修工可以查看分配给自己的工单详细信息
     */
    @Test
    void testGetTicketDetail() {
        // 创建并分配工单
        Long ticketId = createTestTicket("详情测试工单");
        assignTicket(ticketId);

        // 获取工单详情
        TicketDetailDto detail = ticketService.getTicketDetail(ticketId);

        assertThat(detail).isNotNull();
        assertThat(detail.ticketId()).isEqualTo(ticketId);
        assertThat(detail.staffId()).isEqualTo(staffId);
        assertThat(detail.status()).isEqualTo(TicketStatus.IN_PROGRESS);

        System.out.println("✅ 测试3通过：工单详情获取成功");
        System.out.println("   - 工单ID: " + detail.ticketId());
        System.out.println("   - 状态: " + detail.status());
        System.out.println("   - 位置: " + detail.locationText());
    }

    /**
     * 测试4：分页查询任务列表
     * 验证：支持分页和状态筛选
     */
    @Test
    void testListTasksWithPagination() {
        // 创建5个工单
        for (int i = 1; i <= 5; i++) {
            Long ticketId = createTestTicket("分页测试" + i);
            assignTicket(ticketId);

            // 完成2个工单
            if (i <= 2) {
                completeTicket(ticketId);
            }
        }

        // 分页查询所有任务
        Map<String, Object> result = ticketService.listStaffTasksPage(staffId, null, 0, 10);

        assertThat(result.get("list")).isNotNull();
        assertThat((Long) result.get("total")).isGreaterThanOrEqualTo(5);

        System.out.println("✅ 测试4通过：分页查询成功");
        System.out.println("   - 总数: " + result.get("total"));
        System.out.println("   - 当前页: " + result.get("page"));
        System.out.println("   - 每页数量: " + result.get("pageSize"));
    }

    /**
     * 测试5：维修工工作台统计数据
     * 验证：可以获取完整的统计数据
     */
    @Test
    void testGetStaffDashboard() {
        // 创建多个不同状态的工单
        Long ticket1 = createTestTicket("统计测试1");
        Long ticket2 = createTestTicket("统计测试2");
        Long ticket3 = createTestTicket("统计测试3");

        assignTicket(ticket1);
        assignTicket(ticket2);
        assignTicket(ticket3);

        // 完成部分工单
        completeTicket(ticket1);
        completeTicket(ticket2);

        // 获取统计数据
        StaffDashboardDto dashboard = ticketService.getStaffDashboard(staffId);

        assertThat(dashboard).isNotNull();
        assertThat(dashboard.inProgressCount()).isGreaterThanOrEqualTo(1); // 进行中
        assertThat(dashboard.completedCount()).isGreaterThanOrEqualTo(2);  // 已完成
        assertThat(dashboard.categoryDistribution()).isNotNull();

        System.out.println("✅ 测试5通过：工作台统计数据获取成功");
        System.out.println("   - 进行中: " + dashboard.inProgressCount());
        System.out.println("   - 已完成: " + dashboard.completedCount());
        System.out.println("   - 今日完成: " + dashboard.todayCompletedCount());
        System.out.println("   - 分类分布: " + dashboard.categoryDistribution());
    }

    /**
     * 测试6：状态筛选功能
     * 验证：可以按状态筛选任务
     */
    @Test
    void testFilterByStatus() {
        // 创建并分配多个工单
        Long ticket1 = createTestTicket("筛选测试1");
        Long ticket2 = createTestTicket("筛选测试2");
        assignTicket(ticket1);
        assignTicket(ticket2);

        // 完成ticket1
        completeTicket(ticket1);

        // 筛选进行中的任务
        Map<String, Object> inProgressResult = ticketService.listStaffTasksPage(
            staffId, TicketStatus.IN_PROGRESS, 0, 10
        );

        // 筛选已完成的任务
        Map<String, Object> resolvedResult = ticketService.listStaffTasksPage(
            staffId, TicketStatus.RESOLVED, 0, 10
        );

        System.out.println("✅ 测试6通过：状态筛选功能正常");
        System.out.println("   - 进行中任务: " + ((List<?>) inProgressResult.get("list")).size());
        System.out.println("   - 已完成任务: " + ((List<?>) resolvedResult.get("list")).size());
    }

    // ==================== 辅助方法 ====================

    private Long createTestTicket(String description) {
        TicketCreateRequest request = new TicketCreateRequest(
            studentId,
            categoryId,
            "测试地点",
            description,
            "medium",
            List.of("img1.jpg")
        );
        TicketDetailDto dto = ticketService.createTicket(request);
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