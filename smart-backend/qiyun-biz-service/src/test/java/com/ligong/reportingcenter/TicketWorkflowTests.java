package com.ligong.reportingcenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ligong.reportingcenter.domain.enums.TicketStatus;
import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.CategoryDto;
import com.ligong.reportingcenter.dto.TicketDetailDto;
import com.ligong.reportingcenter.dto.request.TicketCreateRequest;
import com.ligong.reportingcenter.dto.request.UserRegisterRequest;
import com.qiyun.common.exception.BusinessException;
import com.ligong.reportingcenter.service.AiAnalysisIntegrationService;
import com.ligong.reportingcenter.service.CategoryService;
import com.ligong.reportingcenter.service.TicketService;
import com.ligong.reportingcenter.service.UserService;
import com.qiyun.feign.client.AiServiceClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工单处理流程测试类
 *
 * 测试完整的工单状态流转：
 * WAITING_ACCEPT（待接单）→ IN_PROGRESS（维修中）→ RESOLVED（已完成）
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketWorkflowTests {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @MockBean
    private AiServiceClient aiServiceClient;

    @MockBean
    private AiAnalysisIntegrationService aiAnalysisIntegrationService;

    private String studentId;
    private String staffId;
    private String staffId2;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        studentId = "workflow_test_student";
        staffId = "workflow_test_staff";
        staffId2 = "workflow_test_staff2";

        // 注册测试用户
        try {
            userService.register(new UserRegisterRequest(studentId, "123456", "测试学生", "13800000001", UserRole.STUDENT));
        } catch (Exception ignored) {}
        try {
            userService.register(new UserRegisterRequest(staffId, "123456", "测试维修工", "13800000002", UserRole.STAFF));
        } catch (Exception ignored) {}
        try {
            userService.register(new UserRegisterRequest(staffId2, "123456", "测试维修工2", "13800000003", UserRole.STAFF));
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

    // ==================== 完整流程测试 ====================

    @Test
    @DisplayName("完整流程：创建工单 → 维修工接单 → 完成工单")
    void testCompleteWorkflow() {
        System.out.println("\n========== 开始测试完整工单流程 ==========");

        // 1. 学生创建工单
        System.out.println("\n步骤1: 学生创建工单");
        Long ticketId = createTestTicket("空调不制冷");
        TicketDetailDto ticket = ticketService.getTicketDetail(ticketId);

        assertThat(ticket.status()).isEqualTo(TicketStatus.WAITING_ACCEPT);
        assertThat(ticket.staffId()).isNull();
        System.out.println("✅ 工单创建成功，状态: " + ticket.status());
        System.out.println("   - 工单ID: " + ticketId);
        System.out.println("   - 描述: " + ticket.description());

        // 2. 维修工接单
        System.out.println("\n步骤2: 维修工主动接单");
        TicketDetailDto acceptedTicket = ticketService.acceptTicket(ticketId, staffId);

        assertThat(acceptedTicket.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(acceptedTicket.staffId()).isEqualTo(staffId);
        assertThat(acceptedTicket.assignedAt()).isNotNull();
        System.out.println("✅ 接单成功，状态: " + acceptedTicket.status());
        System.out.println("   - 维修工: " + acceptedTicket.staffName());
        System.out.println("   - 分配时间: " + acceptedTicket.assignedAt());

        // 3. 维修工完成工单
        System.out.println("\n步骤3: 维修工完成工单");
        TicketDetailDto resolvedTicket = ticketService.resolveTicket(ticketId, staffId, "已清洗滤网，添加制冷剂");

        assertThat(resolvedTicket.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(resolvedTicket.completedAt()).isNotNull();
        assertThat(resolvedTicket.repairNotes()).isEqualTo("已清洗滤网，添加制冷剂");
        System.out.println("✅ 工单完成，状态: " + resolvedTicket.status());
        System.out.println("   - 完成时间: " + resolvedTicket.completedAt());
        System.out.println("   - 维修备注: " + resolvedTicket.repairNotes());

        System.out.println("\n========== 完整流程测试通过 ==========\n");
    }

    // ==================== 接单功能测试 ====================

    @Test
    @DisplayName("接单测试：非维修工不能接单")
    void testNonStaffCannotAccept() {
        Long ticketId = createTestTicket("测试工单");

        // 学生尝试接单应该失败
        assertThrows(BusinessException.class, () -> {
            ticketService.acceptTicket(ticketId, studentId);
        });

        System.out.println("✅ 非维修工无法接单");
    }

    @Test
    @DisplayName("接单测试：不能重复接单")
    void testCannotAcceptTwice() {
        Long ticketId = createTestTicket("测试工单");

        // 第一次接单成功
        ticketService.acceptTicket(ticketId, staffId);

        // 第二次接单应该失败
        assertThrows(BusinessException.class, () -> {
            ticketService.acceptTicket(ticketId, staffId2);
        });

        System.out.println("✅ 不能重复接单");
    }

    @Test
    @DisplayName("接单测试：非待接单状态不能接单")
    void testCannotAcceptNonWaitingTicket() {
        Long ticketId = createTestTicket("测试工单");

        // 接单
        ticketService.acceptTicket(ticketId, staffId);

        // 尝试再次接单应该失败
        assertThrows(BusinessException.class, () -> {
            ticketService.acceptTicket(ticketId, staffId2);
        });

        System.out.println("✅ 非待接单状态无法接单");
    }

    // ==================== 完成功能测试 ====================

    @Test
    @DisplayName("完成测试：只能完成自己的工单")
    void testCanOnlyResolveOwnTicket() {
        Long ticketId = createTestTicket("测试工单");

        // staff 接单
        ticketService.acceptTicket(ticketId, staffId);

        // staff2 尝试完成应该失败
        assertThrows(BusinessException.class, () -> {
            ticketService.resolveTicket(ticketId, staffId2, "测试备注");
        });

        System.out.println("✅ 只能完成分配给自己的工单");
    }

    @Test
    @DisplayName("完成测试：非处理中状态不能完成")
    void testCannotResolveNonInProgressTicket() {
        Long ticketId = createTestTicket("测试工单");

        // 工单还在待接单状态，尝试完成应该失败
        assertThrows(BusinessException.class, () -> {
            ticketService.resolveTicket(ticketId, staffId, "测试备注");
        });

        System.out.println("✅ 非处理中状态无法完成");
    }

    @Test
    @DisplayName("完成测试：可以不带备注完成")
    void testResolveWithoutNotes() {
        Long ticketId = createTestTicket("测试工单");

        // 接单
        ticketService.acceptTicket(ticketId, staffId);

        // 不带备注完成
        TicketDetailDto resolved = ticketService.resolveTicket(ticketId, staffId, null);

        assertThat(resolved.status()).isEqualTo(TicketStatus.RESOLVED);
        System.out.println("✅ 可以不带备注完成工单");
    }

    // ==================== 状态流转边界测试 ====================

    @Test
    @DisplayName("边界测试：状态流转完整性")
    void testStatusTransitionIntegrity() {
        Long ticketId = createTestTicket("边界测试工单");

        // 验证初始状态
        TicketDetailDto ticket1 = ticketService.getTicketDetail(ticketId);
        assertThat(ticket1.status()).isEqualTo(TicketStatus.WAITING_ACCEPT);

        // 接单后验证
        TicketDetailDto ticket2 = ticketService.acceptTicket(ticketId, staffId);
        assertThat(ticket2.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket2.staffId()).isEqualTo(staffId);

        // 完成后验证
        TicketDetailDto ticket3 = ticketService.resolveTicket(ticketId, staffId, "维修完成");
        assertThat(ticket3.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket3.completedAt()).isNotNull();
        assertThat(ticket3.repairNotes()).isNotNull();

        System.out.println("✅ 状态流转完整性验证通过");
        System.out.println("   WAITING_ACCEPT → IN_PROGRESS → RESOLVED");
    }

    // ==================== 辅助方法 ====================

    private Long createTestTicket(String description) {
        TicketCreateRequest request = new TicketCreateRequest(
            studentId,
            categoryId,
            "测试地点-宿舍楼3号楼402",
            description,
            "medium",
            List.of("test.jpg")
        );
        TicketDetailDto dto = ticketService.createTicket(request);
        return dto.ticketId();
    }
}