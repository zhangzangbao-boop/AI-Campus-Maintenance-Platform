package com.qiyun.repairservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.feign.client.AiServiceClient;
import com.qiyun.feign.dto.AnalyzeTicketResponse;
import com.qiyun.repairservice.controller.InternalStatsController;
import com.qiyun.repairservice.domain.entity.Category;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.FeedbackFollowUpStatus;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.dto.StaffRecommendationDto;
import com.qiyun.repairservice.dto.TicketDetailDto;
import com.qiyun.repairservice.dto.TicketSummaryDto;
import com.qiyun.repairservice.dto.request.FeedbackFollowUpUpdateRequest;
import com.qiyun.repairservice.dto.request.TicketAssignRequest;
import com.qiyun.repairservice.dto.request.TicketCreateRequest;
import com.qiyun.repairservice.dto.request.TicketRatingRequest;
import com.qiyun.repairservice.dto.request.TicketStatusUpdateRequest;
import com.qiyun.repairservice.repository.CategoryRepository;
import com.qiyun.repairservice.repository.NotificationRepository;
import com.qiyun.repairservice.repository.TicketRepository;
import com.qiyun.repairservice.repository.UserReferenceRepository;
import com.qiyun.repairservice.service.FeedbackFollowUpService;
import com.qiyun.repairservice.service.FeedbackSentimentAnalysisService;
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

    @Autowired
    private FeedbackSentimentAnalysisService feedbackSentimentAnalysisService;

    @Autowired
    private FeedbackFollowUpService feedbackFollowUpService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private InternalStatsController internalStatsController;

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
        createUserIfNotExists(studentId, "Test Student", UserRole.STUDENT, "13800138001");
        createUserIfNotExists(staffId, "Test Staff", UserRole.STAFF, "13800138002");
        createUserIfNotExists(adminId, "Test Admin", UserRole.ADMIN, "13800138003");

        // Get or create test category
        List<Category> categories = categoryRepository.findAll();
        if (categories.isEmpty()) {
            Category category = new Category();
            category.setCategoryName("Test Category");
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
                "Dorm 3 Room 402",
                "Air conditioner not cooling",
                "medium",
                List.of("https://example.com/img1.jpg", "https://example.com/img2.jpg")
        );

        TicketDetailDto detailDto = ticketService.createTicket(createRequest, null);

        assertThat(detailDto).isNotNull();
        assertThat(detailDto.ticketId()).isNotNull();
        assertThat(detailDto.status()).isEqualTo(TicketStatus.WAITING_ACCEPT);
        assertThat(detailDto.studentId()).isEqualTo(studentId);
        assertThat(detailDto.locationText()).isEqualTo("Dorm 3 Room 402");
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

        // Generic status updates cannot skip the student's confirmation.
        TicketStatusUpdateRequest feedbackRequest = new TicketStatusUpdateRequest(
                staffId,
                TicketStatus.WAITING_FEEDBACK,
                null
        );
        assertThrows(BusinessException.class, () -> ticketService.updateStatus(ticketId, feedbackRequest));

        TicketDetailDto feedbackDto = ticketService.confirmCompletion(ticketId, studentId);
        assertThat(feedbackDto.status()).isEqualTo(TicketStatus.WAITING_FEEDBACK);
        assertThat(feedbackDto.studentConfirmedAt()).isNotNull();
    }

    @Test
    void testRejectTicket() {
        Long ticketId = createTestTicket();

        TicketStatusUpdateRequest rejectRequest = new TicketStatusUpdateRequest(
                adminId,
                TicketStatus.REJECTED,
                "Out of scope"
        );
        TicketDetailDto rejectedDto = ticketService.updateStatus(ticketId, rejectRequest);

        assertThat(rejectedDto.status()).isEqualTo(TicketStatus.REJECTED);
        assertThat(rejectedDto.rejectionReason()).isEqualTo("Out of scope");
        assertThat(rejectedDto.staffId()).isNull();
        assertThat(rejectedDto.closedAt()).isNotNull();
    }

    @Test
    void testRateTicket() {
        Long ticketId = createAssignedAndResolvedTicket();

        TicketRatingRequest ratingRequest = new TicketRatingRequest(studentId, 5, "Satisfied", 5, 5, 5, true, false);
        TicketDetailDto ratedDto = ticketService.rateTicket(ticketId, ratingRequest);

        assertThat(ratedDto.status()).isEqualTo(TicketStatus.FEEDBACKED);
        assertThat(ratedDto.rating()).isNotNull();
        assertThat(ratedDto.rating().score()).isEqualTo(5);
        assertThat(ratedDto.rating().comment()).isEqualTo("Satisfied");
    }

    @Test
    void testFeedbackSentimentFallbackAnalysisPersistsResult() {
        Long ticketId = createAssignedAndResolvedTicket();

        TicketRatingRequest ratingRequest = new TicketRatingRequest(studentId, 2, "\u7b49\u5f85\u592a\u6162\uff0c\u95ee\u9898\u4ecd\u7136\u6ca1\u89e3\u51b3", 2, 2, 3, false, false);
        TicketDetailDto ratedDto = ticketService.rateTicket(ticketId, ratingRequest);

        var response = feedbackSentimentAnalysisService.analyzeNow(ratedDto.rating().ratingId());
        TicketDetailDto detailDto = ticketService.getTicketDetail(ticketId);

        assertThat(response.sentiment()).isEqualTo("NEGATIVE");
        assertThat(detailDto.rating().sentiment()).isEqualTo("NEGATIVE");
        assertThat(detailDto.rating().sentimentScore()).isBetween(0.0, 1.0);
        assertThat(detailDto.rating().sentimentKeywords()).isNotEmpty();
        assertThat(detailDto.rating().sentimentSummary()).isNotBlank();
        assertThat(detailDto.rating().followUpStatus()).isEqualTo("PENDING");
    }


    @Test
    void testFeedbackFollowUpStatusRulesAndBackendFilterStayConsistent() {
        Long noNeedTicketId = createAssignedAndResolvedTicket();
        TicketDetailDto noNeed = ticketService.rateTicket(
            noNeedTicketId,
            new TicketRatingRequest(studentId, 5, "Satisfied", 5, 5, 5, true, false)
        );
        assertThat(noNeed.rating().followUpStatus()).isEqualTo("NO_NEED");

        Long pendingTicketId = createAssignedAndResolvedTicket();
        TicketDetailDto pending = ticketService.rateTicket(
            pendingTicketId,
            new TicketRatingRequest(studentId, 5, "Service was polite but the issue is not fixed", 5, 5, 5, false, false)
        );
        assertThat(pending.rating().followUpStatus()).isEqualTo("PENDING");

        Long handledTicketId = createAssignedAndResolvedTicket();
        TicketDetailDto handledCandidate = ticketService.rateTicket(
            handledTicketId,
            new TicketRatingRequest(studentId, 1, "Still broken", 1, 1, 1, false, false)
        );
        var handled = feedbackFollowUpService.updateFollowUp(
            handledCandidate.rating().ratingId(),
            new FeedbackFollowUpUpdateRequest(adminId, FeedbackFollowUpStatus.HANDLED, "Called student")
        );
        assertThat(handled.followUpStatus()).isEqualTo("HANDLED");

        Map<String, Object> response = internalStatsController
            .getFeedbacks(0, 10, null, null, "PENDING", null, null)
            .getBody();
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        List<com.qiyun.repairservice.dto.RatingDto> list = (List<com.qiyun.repairservice.dto.RatingDto>) data.get("list");

        assertThat(((Number) data.get("total")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(list).extracting(com.qiyun.repairservice.dto.RatingDto::ratingId)
            .contains(pending.rating().ratingId());
        assertThat(list).allMatch(item -> "PENDING".equals(item.followUpStatus()));
    }
    @Test
    void testStudentRejectsCompletionReturnsTicketToWaitingAccept() {
        Long ticketId = createAndAssignTestTicket();
        ticketService.resolveTicket(ticketId, staffId, "Fixed once");
        UserReference staff = userReferenceRepository.findByUserId(staffId).orElseThrow();

        TicketDetailDto returned = ticketService.rejectCompletion(ticketId, studentId, "Still leaking");

        assertThat(returned.status()).isEqualTo(TicketStatus.WAITING_ACCEPT);
        assertThat(returned.completedAt()).isNull();
        assertThat(returned.studentConfirmedAt()).isNull();
        assertThat(returned.studentRejectionReason()).isEqualTo("Still leaking");
        assertThat(returned.logs()).anyMatch(log ->
            log.oldStatus() == TicketStatus.RESOLVED && log.newStatus() == TicketStatus.WAITING_ACCEPT
        );
        assertThat(notificationRepository.findByReceiverOrderByCreatedAtDesc(staff))
            .anyMatch(notification -> "Student returned the repair task".equals(notification.getTitle()));
    }

    @Test
    void testOnlyOwnerCanConfirmCompletionAndDuplicateConfirmIsRejected() {
        Long ticketId = createAndAssignTestTicket();
        ticketService.resolveTicket(ticketId, staffId, "Fixed");
        UserReference student = userReferenceRepository.findByUserId(studentId).orElseThrow();
        UserReference staff = userReferenceRepository.findByUserId(staffId).orElseThrow();

        assertThat(notificationRepository.findByReceiverOrderByCreatedAtDesc(student))
            .anyMatch(notification -> "Repair completed, please confirm".equals(notification.getTitle()));

        assertThrows(BusinessException.class, () -> ticketService.confirmCompletion(ticketId, "other_student"));

        TicketDetailDto confirmed = ticketService.confirmCompletion(ticketId, studentId);
        assertThat(confirmed.status()).isEqualTo(TicketStatus.WAITING_FEEDBACK);
        assertThat(notificationRepository.findByReceiverOrderByCreatedAtDesc(staff))
            .anyMatch(notification -> "Student confirmed completion".equals(notification.getTitle()));

        assertThrows(BusinessException.class, () -> ticketService.confirmCompletion(ticketId, studentId));
    }

    @Test
    void testAdminCanResolveLowRatingFollowUpAndNotifyStudent() {
        Long ticketId = createAssignedAndResolvedTicket();
        TicketRatingRequest ratingRequest = new TicketRatingRequest(
            studentId,
            1,
            "Still broken",
            1,
            1,
            2,
            false,
            false
        );
        TicketDetailDto ratedDto = ticketService.rateTicket(ticketId, ratingRequest);

        var updated = feedbackFollowUpService.updateFollowUp(
            ratedDto.rating().ratingId(),
            new FeedbackFollowUpUpdateRequest(adminId, FeedbackFollowUpStatus.HANDLED, "Called student and arranged rework")
        );
        UserReference student = userReferenceRepository.findByUserId(studentId).orElseThrow();

        assertThat(ratedDto.rating().followUpStatus()).isEqualTo("PENDING");
        assertThat(updated.followUpStatus()).isEqualTo("HANDLED");
        assertThat(updated.followUpOperatorId()).isEqualTo(adminId);
        assertThat(updated.followUpUpdatedAt()).isNotNull();
        assertThat(notificationRepository.findByReceiverOrderByCreatedAtDesc(student))
            .anyMatch(notification -> "反馈回访已处理".equals(notification.getTitle())
                && notification.getContent().contains("管理员已处理您对工单的反馈")
                && ticketId.equals(notification.getRelatedOrderId()));
        assertThat(ticketService.getTicketDetail(ticketId).status()).isEqualTo(TicketStatus.FEEDBACKED);
        long notificationCount = notificationRepository.findByReceiverOrderByCreatedAtDesc(student).stream()
            .filter(notification -> "反馈回访已处理".equals(notification.getTitle()))
            .count();

        feedbackFollowUpService.updateFollowUp(
            ratedDto.rating().ratingId(),
            new FeedbackFollowUpUpdateRequest(adminId, FeedbackFollowUpStatus.HANDLED, "No duplicate notification")
        );

        assertThat(notificationRepository.findByReceiverOrderByCreatedAtDesc(student).stream()
            .filter(notification -> "反馈回访已处理".equals(notification.getTitle()))
            .count()).isEqualTo(notificationCount);
    }

    @Test
    void testFeedbackWithoutFollowUpNeedCannotBeHandled() {
        Long ticketId = createAssignedAndResolvedTicket();
        TicketDetailDto ratedDto = ticketService.rateTicket(
            ticketId,
            new TicketRatingRequest(studentId, 5, "Satisfied", 5, 5, 5, true, false)
        );

        assertThat(ratedDto.rating().followUpStatus()).isEqualTo("NO_NEED");
        assertThrows(BusinessException.class, () -> feedbackFollowUpService.updateFollowUp(
            ratedDto.rating().ratingId(),
            new FeedbackFollowUpUpdateRequest(adminId, FeedbackFollowUpStatus.HANDLED, "No follow-up needed")
        ));
    }

    @Test
    void testFeedbackFollowUpRequiresProcessingNoteAndDoesNotNotifyOnFailure() {
        Long ticketId = createAssignedAndResolvedTicket();
        TicketDetailDto ratedDto = ticketService.rateTicket(
            ticketId,
            new TicketRatingRequest(studentId, 1, "Still broken", 1, 1, 2, false, false)
        );
        UserReference student = userReferenceRepository.findByUserId(studentId).orElseThrow();

        assertThrows(BusinessException.class, () -> feedbackFollowUpService.updateFollowUp(
            ratedDto.rating().ratingId(),
            new FeedbackFollowUpUpdateRequest(adminId, FeedbackFollowUpStatus.HANDLED, "   ")
        ));

        assertThat(ticketService.getTicketDetail(ticketId).rating().followUpStatus()).isEqualTo("PENDING");
        assertThat(notificationRepository.findByReceiverOrderByCreatedAtDesc(student).stream()
            .filter(notification -> "反馈回访已处理".equals(notification.getTitle()))
            .count()).isZero();
    }

    @Test
    void testStaffRecommendationPrefersAreaAndSpecialtyMatch() {
        Long airConditionerCategoryId = ensureCategory("Air Conditioning");
        createUserIfNotExists("test_staff_area_match", "Area Match Staff", UserRole.STAFF, "13800138101");
        createUserIfNotExists("test_staff_no_match", "No Match Staff", UserRole.STAFF, "13800138102");
        UserReference matchedStaff = userReferenceRepository.findByUserId("test_staff_area_match").orElseThrow();
        matchedStaff.setResponsibleArea("Dorm 3");
        matchedStaff.setSpecialties("Electrical,Air conditioning");
        userReferenceRepository.save(matchedStaff);
        UserReference unmatchedStaff = userReferenceRepository.findByUserId("test_staff_no_match").orElseThrow();
        unmatchedStaff.setResponsibleArea("Library");
        unmatchedStaff.setSpecialties("Plumbing");
        userReferenceRepository.save(unmatchedStaff);

        TicketDetailDto ticket = ticketService.createTicket(new TicketCreateRequest(
            studentId,
            airConditionerCategoryId,
            "Dorm 3 Room 402",
            "Air conditioner power failure",
            "medium",
            List.of()
        ), null);

        List<StaffRecommendationDto> recommendations = ticketService.recommendStaffForTicket(ticket.ticketId());

        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations.get(0).staffId()).isEqualTo("test_staff_area_match");
        assertThat(recommendations.get(0).areaMatched()).isTrue();
        assertThat(recommendations.get(0).specialtyMatched()).isTrue();
        assertThat(recommendations.get(0).reason()).isNotBlank();
    }

    @Test
    void testStaffRecommendationFallsBackWhenProfileMissing() {
        Long ticketId = createTestTicket();
        UserReference staff = userReferenceRepository.findByUserId(staffId).orElseThrow();
        staff.setResponsibleArea(null);
        staff.setSpecialties(null);
        userReferenceRepository.save(staff);

        List<StaffRecommendationDto> recommendations = ticketService.recommendStaffForTicket(ticketId);

        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations)
            .anyMatch(item -> item.staffId().equals(staffId)
                && !item.areaMatched()
                && !item.specialtyMatched()
                && item.reason() != null && !item.reason().isBlank());
    }

    @Test
    void testRegenerateCompletionSummary() {
        Long ticketId = createAndAssignTestTicket();
        ticketService.resolveTicket(ticketId, staffId, "Checked wiring and replaced broken switch");

        TicketDetailDto summaryDto = ticketService.getTicketDetail(ticketId);
        assertThat(summaryDto.completionSummary()).isNull();

        when(aiServiceClient.analyzeTicket(any())).thenReturn(Map.of(
            "data", Map.of(
                "category", "Electrical",
                "urgency", "Normal",
                "suggestion", "Observe switch and wiring",
                "keywords", List.of("switch", "wiring")
            )
        ));
        var completionSummary = ticketService.regenerateCompletionSummary(ticketId);

        assertThat(completionSummary).isNotNull();
        assertThat(completionSummary.summary()).isNotBlank();
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
    void adminTicketPageShouldUseDatabasePagingWithoutDuplicatesOrMissingRows() {
        String keyword = "admin-page-unique";
        for (int i = 0; i < 12; i++) {
            createTicketWithDescription(keyword + "-" + i);
        }

        Map<String, Object> page1 = ticketService.listAdminTicketsPage(null, null, keyword, false, 0, 5);
        Map<String, Object> page2 = ticketService.listAdminTicketsPage(null, null, keyword, false, 1, 5);
        Map<String, Object> page3 = ticketService.listAdminTicketsPage(null, null, keyword, false, 2, 5);

        List<Long> ids = new java.util.ArrayList<>();
        ids.addAll(ids(page1));
        ids.addAll(ids(page2));
        ids.addAll(ids(page3));

        assertThat(page1.get("total")).isEqualTo(12L);
        assertThat(page1.get("totalPages")).isEqualTo(3);
        assertThat((List<TicketSummaryDto>) page1.get("list")).hasSize(5);
        assertThat((List<TicketSummaryDto>) page2.get("list")).hasSize(5);
        assertThat((List<TicketSummaryDto>) page3.get("list")).hasSize(2);
        assertThat(ids).hasSize(12);
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void slaOverviewShouldCountOnlyUniqueActiveNotDeletedTickets() {
        Map<String, Object> baseline = ticketService.getSlaOverview();
        int baselineActive = ((Number) baseline.get("activeCount")).intValue();
        int baselineOverdueAccept = ((Number) baseline.get("overdueAcceptCount")).intValue();
        int baselineOverdueCompletion = ((Number) baseline.get("overdueCompletionCount")).intValue();
        int baselineAlertTotal = ((Number) baseline.get("alertTotal")).intValue();

        Long overdueWaiting = createTicketWithDescription("sla-unique-overdue-waiting");
        ageTicket(overdueWaiting, 30, false);

        Long overdueInProgress = createAndAssignTicketWithDescription("sla-unique-overdue-progress");
        ageTicket(overdueInProgress, 30, true);

        Long normalActive = createTicketWithDescription("sla-unique-normal-active");

        Long completed = createAssignedAndResolvedTicket();
        ageTicket(completed, 30, true);

        Long deletedActive = createTicketWithDescription("sla-unique-deleted-active");
        ticketRepository.findById(deletedActive).ifPresent(ticket -> {
            ticket.setDeleted(true);
            ticket.setDeletedAt(LocalDateTime.now());
        });

        Map<String, Object> overview = ticketService.getSlaOverview();

        assertThat(overview.get("activeCount")).isEqualTo(baselineActive + 3);
        assertThat(overview.get("uniqueActiveCount")).isEqualTo(baselineActive + 3);
        assertThat(overview.get("overdueAcceptCount")).isEqualTo(baselineOverdueAccept + 1);
        assertThat(overview.get("overdueCompletionCount")).isEqualTo(baselineOverdueCompletion + 1);
        assertThat(overview.get("alertTotal")).isEqualTo(baselineAlertTotal + 2);

        List<Map<String, Object>> alertTickets = (List<Map<String, Object>>) overview.get("alertTickets");
        List<Long> newAlertIds = alertTickets.stream()
            .map(item -> ((Number) item.get("ticketId")).longValue())
            .filter(id -> id.equals(overdueWaiting) || id.equals(overdueInProgress) || id.equals(normalActive) || id.equals(completed) || id.equals(deletedActive))
            .toList();

        assertThat(newAlertIds).containsExactlyInAnyOrder(overdueWaiting, overdueInProgress);
        assertThat(newAlertIds).doesNotHaveDuplicates();
        double expectedRate = Math.round((baselineOverdueAccept + baselineOverdueCompletion + 2) * 1000.0 / (baselineActive + 3)) / 10.0;
        assertThat(overview.get("overdueRate")).isEqualTo(expectedRate);
    }

    @Test
    void testStudentTicketScopesAreExclusiveAndPagedByBackend() {
        Long waiting = createTestTicket();
        Long progress = createAndAssignTestTicket();
        Long resolved = createAndAssignTestTicket();
        ticketService.resolveTicket(resolved, staffId, "Fixed, waiting for confirmation");
        Long waitingFeedback = createAssignedAndResolvedTicket();
        Long feedbacked = createAssignedAndResolvedTicket();
        ticketService.rateTicket(feedbacked, new TicketRatingRequest(studentId, 5, "Done", 5, 5, 5, true, false));
        Long closed = createAssignedAndResolvedTicket();
        ticketService.rateTicket(closed, new TicketRatingRequest(studentId, 5, "Close it", 5, 5, 5, true, false));
        ticketService.updateStatus(closed, new TicketStatusUpdateRequest(adminId, TicketStatus.CLOSED, null));
        Long rejected = createTestTicket();
        ticketService.updateStatus(rejected, new TicketStatusUpdateRequest(adminId, TicketStatus.REJECTED, "Out of scope"));
        String otherStudentId = "test_student_002";
        createUserIfNotExists(otherStudentId, "Other Student", UserRole.STUDENT, "13800138004");
        createTicketForStudent(otherStudentId);

        Map<String, Object> progressResult = ticketService.listStudentTicketsPage(studentId, null, "progress", null, null, null, 0, 10);
        assertThat(statuses(progressResult)).containsExactlyInAnyOrder(
            TicketStatus.WAITING_ACCEPT,
            TicketStatus.IN_PROGRESS,
            TicketStatus.RESOLVED
        );
        assertThat(ids(progressResult)).containsExactlyInAnyOrder(waiting, progress, resolved);
        assertThat(progressResult.get("total")).isEqualTo(3L);
        Map<String, Object> globalStats = stats(progressResult);
        assertThat(globalStats.get("total")).isEqualTo(7L);
        assertThat(globalStats.get("pending")).isEqualTo(1L);
        assertThat(globalStats.get("processing")).isEqualTo(1L);
        assertThat(globalStats.get("awaitingConfirmation")).isEqualTo(1L);
        assertThat(globalStats.get("toBeEvaluated")).isEqualTo(1L);
        assertThat(globalStats.get("completed")).isEqualTo(2L);
        assertThat(globalStats.get("rejected")).isEqualTo(1L);

        Map<String, Object> feedbackResult = ticketService.listStudentTicketsPage(studentId, null, "to_evaluate", null, null, null, 0, 10);
        assertThat(ids(feedbackResult)).containsExactly(waitingFeedback);
        assertThat(statuses(feedbackResult)).containsExactly(TicketStatus.WAITING_FEEDBACK);
        assertThat(stats(feedbackResult)).isEqualTo(globalStats);

        Map<String, Object> historyResult = ticketService.listStudentTicketsPage(studentId, null, "history", null, null, null, 0, 10);
        assertThat(statuses(historyResult)).containsExactlyInAnyOrder(
            TicketStatus.FEEDBACKED,
            TicketStatus.CLOSED,
            TicketStatus.REJECTED
        );
        assertThat(ids(historyResult)).containsExactlyInAnyOrder(feedbacked, closed, rejected);
        assertThat(stats(historyResult)).isEqualTo(globalStats);

        Map<String, Object> invalidStatusForHistory = ticketService.listStudentTicketsPage(
            studentId, TicketStatus.WAITING_ACCEPT, "history", null, null, null, 0, 10
        );
        assertThat((List<TicketSummaryDto>) invalidStatusForHistory.get("list")).isEmpty();
        assertThat(invalidStatusForHistory.get("total")).isEqualTo(0L);
        assertThat(stats(invalidStatusForHistory)).isEqualTo(globalStats);

        Map<String, Object> pagedProgress = ticketService.listStudentTicketsPage(studentId, null, "progress", null, null, null, 0, 2);
        assertThat((List<TicketSummaryDto>) pagedProgress.get("list")).hasSize(2);
        assertThat(pagedProgress.get("total")).isEqualTo(3L);
        assertThat(stats(pagedProgress)).isEqualTo(globalStats);
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
        TicketRatingRequest ratingRequest = new TicketRatingRequest(studentId, 5, "Satisfied", 5, 5, 5, true, false);
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
        TicketRatingRequest ratingRequest = new TicketRatingRequest(studentId, 5, "Satisfied", 5, 5, 5, true, false);
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


    private List<Long> ids(Map<String, Object> result) {
        return ((List<TicketSummaryDto>) result.get("list")).stream()
            .map(TicketSummaryDto::ticketId)
            .toList();
    }

    private List<TicketStatus> statuses(Map<String, Object> result) {
        return ((List<TicketSummaryDto>) result.get("list")).stream()
            .map(TicketSummaryDto::status)
            .toList();
    }

    private Map<String, Object> stats(Map<String, Object> result) {
        return (Map<String, Object>) result.get("stats");
    }
    // Helper methods

    private Long createTestTicket() {
        return createTicketForStudent(studentId);
    }

    private Long createTicketWithDescription(String description) {
        TicketCreateRequest request = new TicketCreateRequest(
                studentId,
                categoryId,
                "Test Location",
                description,
                "medium",
                List.of("img1.jpg")
        );
        return ticketService.createTicket(request, null).ticketId();
    }

    private Long createTicketForStudent(String ticketStudentId) {
        TicketCreateRequest request = new TicketCreateRequest(
                ticketStudentId,
                categoryId,
                "Test Location",
                "Test Description",
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

    private Long createAndAssignTicketWithDescription(String description) {
        Long ticketId = createTicketWithDescription(description);
        ticketService.assignTicket(ticketId, new TicketAssignRequest(adminId, staffId));
        return ticketId;
    }

    private void ageTicket(Long ticketId, int days, boolean includeAssignedAt) {
        ticketRepository.findById(ticketId).ifPresent(ticket -> {
            LocalDateTime oldTime = LocalDateTime.now().minusDays(days);
            ticket.setCreatedAt(oldTime);
            if (includeAssignedAt) {
                ticket.setAssignedAt(oldTime);
            }
        });
    }

    private Long createAssignedAndResolvedTicket() {
        Long ticketId = createAndAssignTestTicket();

        // Update to resolved
        ticketService.updateStatus(ticketId, new TicketStatusUpdateRequest(
                staffId,
                TicketStatus.RESOLVED,
                null
        ));

        return ticketService.confirmCompletion(ticketId, studentId).ticketId();
    }

    private Long ensureCategory(String categoryName) {
        return categoryRepository.findByCategoryName(categoryName)
            .map(Category::getCategoryId)
            .orElseGet(() -> {
                Category category = new Category();
                category.setCategoryName(categoryName);
                return categoryRepository.save(category).getCategoryId();
            });
    }
}
