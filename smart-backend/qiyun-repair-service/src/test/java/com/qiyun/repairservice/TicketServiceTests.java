package com.qiyun.repairservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.feign.client.AiServiceClient;
import com.qiyun.feign.dto.AnalyzeTicketResponse;
import com.qiyun.repairservice.domain.entity.Category;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.dto.TicketDetailDto;
import com.qiyun.repairservice.dto.TicketSummaryDto;
import com.qiyun.repairservice.dto.request.TicketAssignRequest;
import com.qiyun.repairservice.dto.request.TicketCreateRequest;
import com.qiyun.repairservice.dto.request.TicketRatingRequest;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketServiceTests {

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
        studentId = "test_student_001";
        staffId = "test_staff_001";
        adminId = "test_admin_001";

        // Create test users using UserReference
        createUserIfNotExists(studentId, "测试学生", UserRole.STUDENT, "13800138001");
        createUserIfNotExists(staffId, "测试维修工", UserRole.STAFF, "13800138002");
        createUserIfNotExists(adminId, "测试管理员", UserRole.ADMIN, "13800138003");

        // Get or create test category
        List<Category> categories = categoryRepository.findAll();
        if (categories.isEmpty()) {
            Category category = new Category();
            category.setCategoryName("测试分类");
            category = categoryRepository.save(category);
            categoryId = category.getCategoryId();
        } else {
            categoryId = categories.get(0).getCategoryId();
        }

        // Mock AI service client - returns null from analyzeTicket to avoid NPE
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
    void testCreateTicket() {
        TicketCreateRequest createRequest = new TicketCreateRequest(
                studentId,
                categoryId,
                "宿舍3号楼402",
                "空调不制冷",
                "medium",
                List.of("https://example.com/img1.jpg", "https://example.com/img2.jpg")
        );

        TicketDetailDto detailDto = ticketService.createTicket(createRequest, null);

        assertThat(detailDto).isNotNull();
        assertThat(detailDto.ticketId()).isNotNull();
        assertThat(detailDto.status()).isEqualTo(TicketStatus.WAITING_ACCEPT);
        assertThat(detailDto.studentId()).isEqualTo(studentId);
        assertThat(detailDto.locationText()).isEqualTo("宿舍3号楼402");
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

        // Update to resolved
        TicketStatusUpdateRequest updateRequest = new TicketStatusUpdateRequest(
                staffId,
                TicketStatus.RESOLVED,
                null
        );
        TicketDetailDto resolvedDto = ticketService.updateStatus(ticketId, updateRequest);

        assertThat(resolvedDto.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(resolvedDto.completedAt()).isNotNull();

        // Update to waiting for feedback
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
    void testRegenerateCompletionSummary() {
        Long ticketId = createAndAssignTestTicket();
        ticketService.resolveTicket(ticketId, staffId, "已检查线路并更换损坏开关，恢复正常使用");

        TicketDetailDto summaryDto = ticketService.getTicketDetail(ticketId);
        assertThat(summaryDto.completionSummary()).isNull();

        when(aiServiceClient.analyzeTicket(any())).thenReturn(Map.of(
            "data", Map.of(
                "category", "电气故障",
                "urgency", "普通",
                "suggestion", "建议后续观察开关和线路状态",
                "keywords", List.of("开关", "线路")
            )
        ));

        var completionSummary = ticketService.regenerateCompletionSummary(ticketId);

        assertThat(completionSummary).isNotNull();
        assertThat(completionSummary.summary()).contains("故障原因", "维修过程", "使用材料", "处理结果", "耗时", "后续建议");
        assertThat(ticketService.getTicketDetail(ticketId).completionSummary().summary()).isEqualTo(completionSummary.summary());
    }
    @Test
    void testListTickets() {
        createTestTicket();
        createTestTicket();

        List<TicketSummaryDto> studentTickets = ticketService.listByStudent(studentId);
        assertThat(studentTickets).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void testInvalidOperations() {
        Long ticketId = createTestTicket();

        // Non-staff cannot be assigned
        TicketAssignRequest invalidAssign = new TicketAssignRequest(adminId, studentId);
        assertThrows(BusinessException.class, () ->
                ticketService.assignTicket(ticketId, invalidAssign)
        );

        // Rejection requires reason
        TicketStatusUpdateRequest invalidReject = new TicketStatusUpdateRequest(
                adminId,
                TicketStatus.REJECTED,
                null
        );
        assertThrows(BusinessException.class, () ->
                ticketService.updateStatus(ticketId, invalidReject)
        );

        // Cannot rate twice
        Long ratedTicketId = createAssignedAndResolvedTicket();
        TicketRatingRequest ratingRequest = new TicketRatingRequest(studentId, 5, "满意", 5, 5, 5, true, false);
        ticketService.rateTicket(ratedTicketId, ratingRequest);

        assertThrows(BusinessException.class, () ->
                ticketService.rateTicket(ratedTicketId, ratingRequest)
        );
    }

    @Test
    void testInvalidStatusTransitions() {
        Long ticketId = createTestTicket();

        // Cannot directly go from WAITING_ACCEPT to FEEDBACKED
        TicketStatusUpdateRequest invalidTransition = new TicketStatusUpdateRequest(
                adminId,
                TicketStatus.FEEDBACKED,
                null
        );
        assertThrows(BusinessException.class, () ->
                ticketService.updateStatus(ticketId, invalidTransition)
        );
    }

    @Test
    void testCloseTicket() {
        Long ticketId = createAssignedAndResolvedTicket();
        TicketRatingRequest ratingRequest = new TicketRatingRequest(studentId, 5, "满意", 5, 5, 5, true, false);
        ticketService.rateTicket(ticketId, ratingRequest);

        TicketStatusUpdateRequest closeRequest = new TicketStatusUpdateRequest(
                adminId,
                TicketStatus.CLOSED,
                null
        );
        TicketDetailDto closedDto = ticketService.updateStatus(ticketId, closeRequest);

        assertThat(closedDto.status()).isEqualTo(TicketStatus.CLOSED);
        assertThat(closedDto.closedAt()).isNotNull();
    }

    @Test
    void testGetTicketDetail() {
        Long ticketId = createTestTicket();

        TicketDetailDto detailDto = ticketService.getTicketDetail(ticketId);

        assertThat(detailDto).isNotNull();
        assertThat(detailDto.ticketId()).isEqualTo(ticketId);
        assertThat(detailDto.status()).isEqualTo(TicketStatus.WAITING_ACCEPT);
    }

    @Test
    void testListByStaff() {
        Long ticketId = createAndAssignTestTicket();

        Map<String, Object> result = ticketService.listStaffTasksPage(staffId, null, 0, 10);
        List<TicketSummaryDto> staffTickets = (List<TicketSummaryDto>) result.get("list");

        assertThat(staffTickets).isNotEmpty();
        assertThat(staffTickets.get(0).staffId()).isEqualTo(staffId);
    }

    // Helper methods

    private Long createTestTicket() {
        TicketCreateRequest request = new TicketCreateRequest(
                studentId,
                categoryId,
                "测试地点",
                "测试描述",
                "medium",
                List.of("img1.jpg", "img2.jpg")
        );
        TicketDetailDto dto = ticketService.createTicket(request, null);
        return dto.ticketId();
    }

    private Long createAndAssignTestTicket() {
        Long ticketId = createTestTicket();
        TicketAssignRequest assignRequest = new TicketAssignRequest(adminId, staffId);
        ticketService.assignTicket(ticketId, assignRequest);
        return ticketId;
    }

    private Long createAssignedAndResolvedTicket() {
        Long ticketId = createAndAssignTestTicket();

        // Update to resolved
        ticketService.updateStatus(ticketId, new TicketStatusUpdateRequest(
                staffId,
                TicketStatus.RESOLVED,
                null
        ));

        // Update to waiting for feedback
        return ticketService.updateStatus(ticketId, new TicketStatusUpdateRequest(
                staffId,
                TicketStatus.WAITING_FEEDBACK,
                null
        )).ticketId();
    }
}