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
import com.qiyun.repairservice.dto.request.TicketCreateRequest;
import com.qiyun.repairservice.repository.CategoryRepository;
import com.qiyun.repairservice.repository.TicketRepository;
import com.qiyun.repairservice.repository.UserReferenceRepository;
import com.qiyun.repairservice.service.TicketService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workflow tests for ticket status transitions
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketWorkflowTests {

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
    private String staffId2;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        studentId = "workflow_test_student";
        staffId = "workflow_test_staff";
        staffId2 = "workflow_test_staff2";

        createUserIfNotExists(studentId, "Workflow Student", UserRole.STUDENT, "13800000001");
        createUserIfNotExists(staffId, "Workflow Staff A", UserRole.STAFF, "13800000002");
        createUserIfNotExists(staffId2, "Workflow Staff B", UserRole.STAFF, "13800000003");

        List<Category> categories = categoryRepository.findAll();
        if (categories.isEmpty()) {
            Category category = new Category();
            category.setCategoryName("娴嬭瘯鍒嗙被");
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

    // ==================== Complete Workflow Tests ====================

    @Test
    @DisplayName("Complete workflow: create -> accept -> resolve")
    void testCompleteWorkflow() {
        // 1. Student creates ticket
        Long ticketId = createTestTicket("Air conditioner is not cooling");
        TicketDetailDto ticket = ticketService.getTicketDetail(ticketId);

        assertThat(ticket.status()).isEqualTo(TicketStatus.WAITING_ACCEPT);
        assertThat(ticket.staffId()).isNull();

        // 2. Staff accepts ticket
        TicketDetailDto acceptedTicket = ticketService.acceptTicket(ticketId, staffId);

        assertThat(acceptedTicket.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(acceptedTicket.staffId()).isEqualTo(staffId);
        assertThat(acceptedTicket.assignedAt()).isNotNull();

        // 3. Staff resolves ticket
        TicketDetailDto resolvedTicket = ticketService.resolveTicket(ticketId, staffId, "Cleaned filter and added coolant");

        assertThat(resolvedTicket.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(resolvedTicket.completedAt()).isNotNull();
        assertThat(resolvedTicket.repairNotes()).isEqualTo("Cleaned filter and added coolant");
    }

    // ==================== Acceptance Tests ====================

    @Test
    @DisplayName("Accept test: non-staff cannot accept")
    void testNonStaffCannotAccept() {
        Long ticketId = createTestTicket("娴嬭瘯宸ュ崟");

        // Student trying to accept should fail
        assertThrows(BusinessException.class, () -> {
            ticketService.acceptTicket(ticketId, studentId);
        });
    }

    @Test
    @DisplayName("Accept test: cannot accept twice")
    void testCannotAcceptTwice() {
        Long ticketId = createTestTicket("娴嬭瘯宸ュ崟");

        // First acceptance succeeds
        ticketService.acceptTicket(ticketId, staffId);

        // Second acceptance should fail
        assertThrows(BusinessException.class, () -> {
            ticketService.acceptTicket(ticketId, staffId2);
        });
    }

    @Test
    @DisplayName("Accept test: non-waiting ticket cannot be accepted")
    void testCannotAcceptNonWaitingTicket() {
        Long ticketId = createTestTicket("娴嬭瘯宸ュ崟");

        // Accept ticket
        ticketService.acceptTicket(ticketId, staffId);

        // Try to accept again should fail
        assertThrows(BusinessException.class, () -> {
            ticketService.acceptTicket(ticketId, staffId2);
        });
    }

    // ==================== Resolve Tests ====================

    @Test
    @DisplayName("Resolve test: can only resolve own ticket")
    void testCanOnlyResolveOwnTicket() {
        Long ticketId = createTestTicket("娴嬭瘯宸ュ崟");

        // staff accepts
        ticketService.acceptTicket(ticketId, staffId);

        // staff2 tries to resolve should fail
        assertThrows(BusinessException.class, () -> {
            ticketService.resolveTicket(ticketId, staffId2, "娴嬭瘯澶囨敞");
        });
    }

    @Test
    @DisplayName("Resolve test: non-in-progress ticket cannot be resolved")
    void testCannotResolveNonInProgressTicket() {
        Long ticketId = createTestTicket("娴嬭瘯宸ュ崟");

        // Ticket is in WAITING_ACCEPT state, trying to resolve should fail
        assertThrows(BusinessException.class, () -> {
            ticketService.resolveTicket(ticketId, staffId, "娴嬭瘯澶囨敞");
        });
    }

    @Test
    @DisplayName("Resolve test: can resolve without notes")
    void testResolveWithoutNotes() {
        Long ticketId = createTestTicket("娴嬭瘯宸ュ崟");

        // Accept ticket
        ticketService.acceptTicket(ticketId, staffId);

        // Resolve without notes
        TicketDetailDto resolved = ticketService.resolveTicket(ticketId, staffId, null);

        assertThat(resolved.status()).isEqualTo(TicketStatus.RESOLVED);
    }


    @Test
    @DisplayName("Reject completion: return to original staff waiting accept and visible today")
    void rejectCompletion_shouldReturnToOriginalStaffWaitingAcceptAndBeVisibleToday() {
        String otherStudentId = "workflow_test_other_student";
        createUserIfNotExists(otherStudentId, "Other student", UserRole.STUDENT, "13800000004");

        Long ticketId = createTestTicket("Air conditioner still leaks after repair");
        ticketService.acceptTicket(ticketId, staffId);

        LocalDateTime oldAssignedAt = LocalDateTime.now().minusDays(3).withNano(0);
        var stored = ticketRepository.findById(ticketId).orElseThrow();
        stored.setAssignedAt(oldAssignedAt);
        ticketRepository.saveAndFlush(stored);

        TicketDetailDto resolved = ticketService.resolveTicket(ticketId, staffId, "Replaced drain pipe");
        assertThat(resolved.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(resolved.completedAt()).isNotNull();

        assertThrows(BusinessException.class,
            () -> ticketService.rejectCompletion(ticketId, otherStudentId, "Not my ticket"));

        TicketDetailDto returned = ticketService.rejectCompletion(ticketId, studentId, "Still leaking at night");
        assertThat(returned.status()).isEqualTo(TicketStatus.WAITING_ACCEPT);
        assertThat(returned.staffId()).isEqualTo(staffId);
        assertThat(returned.completedAt()).isNull();
        assertThat(returned.repairNotes()).isEqualTo("Replaced drain pipe");
        assertThat(returned.studentRejectionReason()).isEqualTo("Still leaking at night");
        assertThat(returned.assignedAt()).isEqualTo(oldAssignedAt);
        assertThat(returned.logs()).anySatisfy(log -> {
            assertThat(log.oldStatus()).isEqualTo(TicketStatus.RESOLVED);
            assertThat(log.newStatus()).isEqualTo(TicketStatus.WAITING_ACCEPT);
            assertThat(log.operatorId()).isEqualTo(studentId);
        });

        assertThrows(BusinessException.class,
            () -> ticketService.rejectCompletion(ticketId, studentId, "Duplicate return"));
        assertThrows(BusinessException.class,
            () -> ticketService.acceptTicket(ticketId, staffId2));

        assertThat(taskIds(ticketService.listStaffTasksPage(staffId, null, "all", null, null, null, 0, 20)))
            .contains(ticketId);
        assertThat(taskIds(ticketService.listStaffTasksPage(staffId, null, "today", null, null, null, 0, 20)))
            .contains(ticketId);

        TicketDetailDto acceptedAgain = ticketService.acceptTicket(ticketId, staffId);
        assertThat(acceptedAgain.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(acceptedAgain.staffId()).isEqualTo(staffId);
    }
    // ==================== Status Transition Boundary Tests ====================

    @Test
    @DisplayName("Boundary test: status transition integrity")
    void testStatusTransitionIntegrity() {
        Long ticketId = createTestTicket("杈圭晫娴嬭瘯宸ュ崟");

        // Verify initial status
        TicketDetailDto ticket1 = ticketService.getTicketDetail(ticketId);
        assertThat(ticket1.status()).isEqualTo(TicketStatus.WAITING_ACCEPT);

        // After acceptance
        TicketDetailDto ticket2 = ticketService.acceptTicket(ticketId, staffId);
        assertThat(ticket2.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket2.staffId()).isEqualTo(staffId);

        // After resolution
        TicketDetailDto ticket3 = ticketService.resolveTicket(ticketId, staffId, "缁翠慨瀹屾垚");
        assertThat(ticket3.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket3.completedAt()).isNotNull();
        assertThat(ticket3.repairNotes()).isNotNull();
    }

    // ==================== Helper Methods ====================

    private Long createTestTicket(String description) {
        TicketCreateRequest request = new TicketCreateRequest(
            studentId,
            categoryId,
            "娴嬭瘯鍦扮偣-瀹胯垗妤?鍙锋ゼ402",
            description,
            "medium",
            List.of("test.jpg")
        );
        TicketDetailDto dto = ticketService.createTicket(request, null);
        return dto.ticketId();
    }

    private List<Long> taskIds(Map<String, Object> result) {
        @SuppressWarnings("unchecked")
        List<com.qiyun.repairservice.dto.TicketSummaryDto> tasks =
            (List<com.qiyun.repairservice.dto.TicketSummaryDto>) result.get("list");
        return tasks.stream().map(com.qiyun.repairservice.dto.TicketSummaryDto::ticketId).toList();
    }
}
