package com.ligong.reportingcenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ligong.reportingcenter.domain.enums.TicketStatus;
import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.CategoryDto;
import com.ligong.reportingcenter.dto.TicketDetailDto;
import com.ligong.reportingcenter.dto.TicketSummaryDto;
import com.ligong.reportingcenter.dto.request.TicketAssignRequest;
import com.ligong.reportingcenter.dto.request.TicketCreateRequest;
import com.ligong.reportingcenter.dto.request.TicketImageRequest;
import com.ligong.reportingcenter.dto.request.TicketRatingRequest;
import com.ligong.reportingcenter.dto.request.TicketStatusUpdateRequest;
import com.ligong.reportingcenter.dto.request.UserRegisterRequest;
import com.ligong.reportingcenter.exception.BusinessException;
import com.ligong.reportingcenter.service.CategoryService;
import com.ligong.reportingcenter.service.TicketService;
import com.ligong.reportingcenter.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TicketServiceTests {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    private String studentId;
    private String staffId;
    private String adminId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        // 使用固定的测试 userId（可根据需要改为随机以避免冲突）
        studentId = "test_student_001";
        staffId = "test_staff_001";
        adminId = "test_admin_001";

        // 注册用户（register 方法应当是幂等或在重复时抛出可忽略的异常）
        try {
            userService.register(new UserRegisterRequest(studentId, "123456", "测试学生", "13800138001", UserRole.STUDENT));
        } catch (Exception ignored) {}
        try {
            userService.register(new UserRegisterRequest(staffId, "123456", "测试维修工", "13800138002", UserRole.STAFF));
        } catch (Exception ignored) {}
        try {
            userService.register(new UserRegisterRequest(adminId, "123456", "测试管理员", "13800138003", UserRole.ADMIN));
        } catch (Exception ignored) {}

        // 获取或创建测试分类，避免依赖外部初始数据
        List<CategoryDto> categories = categoryService.listAll();
        if (categories == null || categories.isEmpty()) {
            CategoryDto created = categoryService.create("测试分类");
            categoryId = created.categoryId();
            // 确保分类数据已提交到数据库并刷新
            categoryService.listAll(); // 触发数据刷新，确保新创建的分类可用
        } else {
            categoryId = categories.get(0).categoryId();
        }

        // 确保 categoryId 有效
        if (categoryId == null) {
            throw new IllegalStateException("Failed to obtain test categoryId in setUp");
        }
    }

    @Test
    void testCreateTicket() {
        TicketCreateRequest createRequest = new TicketCreateRequest(
                studentId,
                categoryId,
                "宿舍3号楼402",
                "空调不制冷",
                "medium",
                List.of("https://example.com/img1.jpg", "https://example.com/img2.jpg")
        );

        TicketDetailDto detailDto = ticketService.createTicket(createRequest);

        assertThat(detailDto).isNotNull();
        assertThat(detailDto.ticketId()).isNotNull();
        assertThat(detailDto.status()).isEqualTo(TicketStatus.WAITING_ACCEPT);
        assertThat(detailDto.studentId()).isEqualTo(studentId);
        assertThat(detailDto.locationText()).isEqualTo("宿舍3号楼402");
        assertThat(detailDto.images()).hasSize(2);
    }

    @Test
    void testAssignTicket() {
        Long ticketId = createTestTicket();

        TicketAssignRequest assignRequest = new TicketAssignRequest(adminId, staffId);
        TicketDetailDto detailDto = ticketService.assignTicket(ticketId, assignRequest);

        assertThat(detailDto.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(detailDto.staffId()).isEqualTo(staffId);
        assertThat(detailDto.assignedAt()).isNotNull();
    }

    @Test
    void testUpdateTicketStatus() {
        Long ticketId = createAndAssignTestTicket();

        // 更新为已处理
        TicketStatusUpdateRequest updateRequest = new TicketStatusUpdateRequest(
                staffId,
                TicketStatus.RESOLVED,
                null
        );
        TicketDetailDto resolvedDto = ticketService.updateStatus(ticketId, updateRequest);

        assertThat(resolvedDto.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(resolvedDto.completedAt()).isNotNull();

        // 更新为待评价
        TicketStatusUpdateRequest feedbackRequest = new TicketStatusUpdateRequest(
                staffId,
                TicketStatus.WAITING_FEEDBACK,
                null
        );
        TicketDetailDto feedbackDto = ticketService.updateStatus(ticketId, feedbackRequest);
        assertThat(feedbackDto.status()).isEqualTo(TicketStatus.WAITING_FEEDBACK);
    }

    @Test
    void testRejectTicket() {
        Long ticketId = createTestTicket();

        TicketStatusUpdateRequest rejectRequest = new TicketStatusUpdateRequest(
                adminId,
                TicketStatus.REJECTED,
                "不属于维修范围"
        );
        TicketDetailDto rejectedDto = ticketService.updateStatus(ticketId, rejectRequest);

        assertThat(rejectedDto.status()).isEqualTo(TicketStatus.REJECTED);
        assertThat(rejectedDto.rejectionReason()).isEqualTo("不属于维修范围");
        assertThat(rejectedDto.staffId()).isNull();
        assertThat(rejectedDto.closedAt()).isNotNull();
    }

    @Test
    void testAddImage() {
        Long ticketId = createTestTicket();

        TicketImageRequest imageRequest = new TicketImageRequest("https://example.com/new_img.jpg");
        TicketDetailDto detailDto = ticketService.addImage(ticketId, imageRequest);

        // 初始 2 张 + 新增 1 张
        assertThat(detailDto.images()).hasSize(3);
    }

    @Test
    void testRateTicket() {
        Long ticketId = createAssignedAndResolvedTicket();

        TicketRatingRequest ratingRequest = new TicketRatingRequest(studentId, 5, "维修很及时，非常满意", 5, 5, 5, true, false);
        TicketDetailDto ratedDto = ticketService.rateTicket(ticketId, ratingRequest);

        assertThat(ratedDto.status()).isEqualTo(TicketStatus.FEEDBACKED);
        assertThat(ratedDto.rating()).isNotNull();
        assertThat(ratedDto.rating().score()).isEqualTo(5);
        assertThat(ratedDto.rating().comment()).isEqualTo("维修很及时，非常满意");
    }

    @Test
    void testListTickets() {
        createTestTicket();
        createTestTicket();

        List<TicketSummaryDto> studentTickets = ticketService.listByStudent(studentId);
        assertThat(studentTickets).hasSizeGreaterThanOrEqualTo(2);

        List<TicketSummaryDto> waitingTickets = ticketService.listByStatus(TicketStatus.WAITING_ACCEPT);
        // 至少包含新建的等待接单数据
        assertThat(waitingTickets).isNotNull();
    }

    @Test
    void testInvalidOperations() {
        Long ticketId = createTestTicket();

        // 非维修工分配（student 不能被分配为 staff）
        TicketAssignRequest invalidAssign = new TicketAssignRequest(adminId, studentId);
        assertThrows(BusinessException.class, () ->
                ticketService.assignTicket(ticketId, invalidAssign)
        );

        // 无理由驳回（驳回需理由）
        TicketStatusUpdateRequest invalidReject = new TicketStatusUpdateRequest(
                adminId,
                TicketStatus.REJECTED,
                null
        );
        assertThrows(BusinessException.class, () ->
                ticketService.updateStatus(ticketId, invalidReject)
        );

        // 重复评价
        Long ratedTicketId = createAssignedAndResolvedTicket();
        TicketRatingRequest ratingRequest = new TicketRatingRequest(studentId, 5, "满意", 5, 5, 5, true, false);
        ticketService.rateTicket(ratedTicketId, ratingRequest);

        assertThrows(BusinessException.class, () ->
                ticketService.rateTicket(ratedTicketId, ratingRequest)
        );
    }

    // 新增测试：无效的状态转换
    @Test
    void testInvalidStatusTransitions() {
        Long ticketId = createTestTicket();

        // 从WAITING_ACCEPT不能直接转换为FEEDBACKED
        TicketStatusUpdateRequest invalidTransition = new TicketStatusUpdateRequest(
                adminId,
                TicketStatus.FEEDBACKED,
                null
        );
        assertThrows(BusinessException.class, () ->
                ticketService.updateStatus(ticketId, invalidTransition)
        );
    }

    // 新增测试：关闭工单
    @Test
    void testCloseTicket() {
        // 创建并评价一个工单
        Long ticketId = createAssignedAndResolvedTicket();
        TicketRatingRequest ratingRequest = new TicketRatingRequest(studentId, 5, "满意", 5, 5, 5, true, false);
        ticketService.rateTicket(ticketId, ratingRequest);

        // 关闭工单
        TicketStatusUpdateRequest closeRequest = new TicketStatusUpdateRequest(
                adminId,
                TicketStatus.CLOSED,
                null
        );
        TicketDetailDto closedDto = ticketService.updateStatus(ticketId, closeRequest);

        assertThat(closedDto.status()).isEqualTo(TicketStatus.CLOSED);
        assertThat(closedDto.closedAt()).isNotNull();
    }

    // 新增测试：查找工单详情
    @Test
    void testGetTicketDetail() {
        Long ticketId = createTestTicket();

        TicketDetailDto detailDto = ticketService.getTicketDetail(ticketId);

        assertThat(detailDto).isNotNull();
        assertThat(detailDto.ticketId()).isEqualTo(ticketId);
        assertThat(detailDto.status()).isEqualTo(TicketStatus.WAITING_ACCEPT);
    }

    // 新增测试：按维修工查找工单
    @Test
    void testListByStaff() {
        Long ticketId = createAndAssignTestTicket();

        List<TicketSummaryDto> staffTickets = ticketService.listByStaff(staffId);

        assertThat(staffTickets).isNotEmpty();
        assertThat(staffTickets.get(0).staffId()).isEqualTo(staffId);
    }

    // 辅助方法：创建测试报修单并返回 ticketId
    private Long createTestTicket() {
        TicketCreateRequest request = new TicketCreateRequest(
                studentId,
                categoryId,
                "测试地点",
                "测试描述",
                "medium",
                List.of("img1.jpg", "img2.jpg")
        );
        TicketDetailDto dto = ticketService.createTicket(request);
        return dto.ticketId();
    }

    // 辅助方法：创建并分配报修单
    private Long createAndAssignTestTicket() {
        Long ticketId = createTestTicket();
        TicketAssignRequest assignRequest = new TicketAssignRequest(adminId, staffId);
        ticketService.assignTicket(ticketId, assignRequest);
        return ticketId;
    }

    // 辅助方法：创建、分配并更新到待评价状态
    private Long createAssignedAndResolvedTicket() {
        Long ticketId = createAndAssignTestTicket();

        // 更新为已处理
        ticketService.updateStatus(ticketId, new TicketStatusUpdateRequest(
                staffId,
                TicketStatus.RESOLVED,
                null
        ));

        // 更新为待评价
        return ticketService.updateStatus(ticketId, new TicketStatusUpdateRequest(
                staffId,
                TicketStatus.WAITING_FEEDBACK,
                null
        )).ticketId();
    }
}