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
import com.qiyun.repairservice.domain.enums.RepairProcessActionType;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.dto.RepairProcessRecordDto;
import com.qiyun.repairservice.dto.request.RepairProcessRecordRequest;
import com.qiyun.repairservice.repository.CategoryRepository;
import com.qiyun.repairservice.repository.UserReferenceRepository;
import com.qiyun.repairservice.service.RepairProcessRecordService;
import com.qiyun.repairservice.service.TicketService;
import java.time.LocalDateTime;
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
 * Repair process record tests for repair-service
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepairProcessRecordTests {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private RepairProcessRecordService processRecordService;

    @Autowired
    private UserReferenceRepository userReferenceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockBean
    private AiServiceClient aiServiceClient;

    private String studentId;
    private String staffId;
    private Long categoryId;
    private Long ticketId;

    @BeforeEach
    void setUp() {
        studentId = "process_test_student";
        staffId = "process_test_staff";

        createUserIfNotExists(studentId, "测试学生", UserRole.STUDENT, "13800000001");
        createUserIfNotExists(staffId, "测试维修工", UserRole.STAFF, "13800000002");

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

        // Create test ticket and accept it
        ticketId = createTestTicket("测试工单");
        ticketService.acceptTicket(ticketId, staffId);
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
    @DisplayName("Complete flow: arrived -> repairing -> finished")
    void testCompleteProcessFlow() {
        // 1. Arrival confirmation
        LocalDateTime arrivedTime = LocalDateTime.now();
        RepairProcessRecordRequest arrivedRequest = new RepairProcessRecordRequest(
            RepairProcessActionType.ARRIVED,
            "已到达宿舍3号楼402",
            null,
            arrivedTime,
            null,
            null,
            null,
            null,
            null
        );
        RepairProcessRecordDto arrivedRecord = processRecordService.addRecord(ticketId, staffId, arrivedRequest);

        assertThat(arrivedRecord.actionType()).isEqualTo(RepairProcessActionType.ARRIVED);
        assertThat(arrivedRecord.arrivedAt()).isNotNull();

        // 2. Repair process
        String materialsJson = "[{\"name\":\"空调滤网\",\"quantity\":1,\"unit\":\"个\"},{\"name\":\"制冷剂\",\"quantity\":2,\"unit\":\"瓶\"}]";
        RepairProcessRecordRequest repairRequest = new RepairProcessRecordRequest(
            RepairProcessActionType.REPAIRING,
            "正在维修空调",
            null,
            null,
            "清洗滤网，添加制冷剂，检查电路",
            materialsJson,
            null,
            45,
            "需要后续观察"
        );
        RepairProcessRecordDto repairRecord = processRecordService.addRecord(ticketId, staffId, repairRequest);

        assertThat(repairRecord.actionType()).isEqualTo(RepairProcessActionType.REPAIRING);
        assertThat(repairRecord.repairDescription()).isNotNull();
        assertThat(repairRecord.materialsUsed()).isNotNull();
        assertThat(repairRecord.durationMinutes()).isEqualTo(45);

        // 3. Finish confirmation
        LocalDateTime finishedTime = LocalDateTime.now();
        RepairProcessRecordRequest finishRequest = new RepairProcessRecordRequest(
            RepairProcessActionType.FINISHED,
            "维修完成，运行正常",
            null,
            null,
            "已完成所有维修工作",
            materialsJson,
            finishedTime,
            60,
            "建议定期清洗滤网"
        );
        RepairProcessRecordDto finishRecord = processRecordService.addRecord(ticketId, staffId, finishRequest);

        assertThat(finishRecord.actionType()).isEqualTo(RepairProcessActionType.FINISHED);
        assertThat(finishRecord.finishedAt()).isNotNull();

        // 4. Query all records
        List<RepairProcessRecordDto> records = processRecordService.listRecords(ticketId, staffId);
        assertThat(records).hasSize(3);
    }

    @Test
    @DisplayName("Arrival record: auto-set arrived time")
    void testArrivedRecordAutoTime() {
        RepairProcessRecordDto record = processRecordService.recordArrival(
            ticketId,
            staffId,
            "已到达现场",
            null
        );

        assertThat(record.actionType()).isEqualTo(RepairProcessActionType.ARRIVED);
        assertThat(record.arrivedAt()).isNotNull();
    }

    @Test
    @DisplayName("Repair record: materials JSON format")
    void testMaterialUsedFormat() {
        String materialsJson = "[{\"name\":\"螺丝\",\"quantity\":10,\"unit\":\"个\"}]";

        RepairProcessRecordDto record = processRecordService.recordRepairProcess(
            ticketId,
            staffId,
            "更换螺丝",
            materialsJson,
            30,
            null
        );

        assertThat(record.materialsUsed()).isEqualTo(materialsJson);
    }

    @Test
    @DisplayName("Finish record: auto-set finished time")
    void testFinishRecordAutoTime() {
        RepairProcessRecordDto record = processRecordService.recordFinish(
            ticketId,
            staffId,
            "维修完成",
            null,
            60,
            "已测试运行正常"
        );

        assertThat(record.actionType()).isEqualTo(RepairProcessActionType.FINISHED);
        assertThat(record.finishedAt()).isNotNull();
        assertThat(record.remarks()).isEqualTo("已测试运行正常");
    }

    @Test
    @DisplayName("Quick method: recordArrival")
    void testRecordArrivalMethod() {
        RepairProcessRecordDto record = processRecordService.recordArrival(
            ticketId,
            staffId,
            "到场确认",
            "http://example.com/arrival.jpg"
        );

        assertThat(record.actionType()).isEqualTo(RepairProcessActionType.ARRIVED);
        assertThat(record.imageUrl()).isEqualTo("http://example.com/arrival.jpg");
        assertThat(record.arrivedAt()).isNotNull();
    }

    @Test
    @DisplayName("Quick method: recordRepairProcess")
    void testRecordRepairProcessMethod() {
        RepairProcessRecordDto record = processRecordService.recordRepairProcess(
            ticketId,
            staffId,
            "正在维修中",
            "[{\"name\":\"配件\",\"quantity\":1}]",
            30,
            "测试备注"
        );

        assertThat(record.actionType()).isEqualTo(RepairProcessActionType.REPAIRING);
        assertThat(record.repairDescription()).isEqualTo("正在维修中");
        assertThat(record.durationMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("Quick method: recordFinish")
    void testRecordFinishMethod() {
        RepairProcessRecordDto record = processRecordService.recordFinish(
            ticketId,
            staffId,
            "维修完成",
            null,
            60,
            null
        );

        assertThat(record.actionType()).isEqualTo(RepairProcessActionType.FINISHED);
        assertThat(record.finishedAt()).isNotNull();
    }

    @Test
    @DisplayName("List records: ordered by time ascending")
    void testListRecordsOrdered() throws InterruptedException {
        // Add multiple records
        processRecordService.recordArrival(ticketId, staffId, "到场", null);
        Thread.sleep(100); // Ensure different timestamps
        processRecordService.recordRepairProcess(ticketId, staffId, "维修中", null, null, null);
        Thread.sleep(100);
        processRecordService.recordFinish(ticketId, staffId, "完成", null, null, null);

        List<RepairProcessRecordDto> records = processRecordService.listRecords(ticketId, staffId);

        // Verify ordered by time ascending
        for (int i = 1; i < records.size(); i++) {
            assertThat(records.get(i).createdAt())
                .isAfterOrEqualTo(records.get(i - 1).createdAt());
        }
    }

    @Test
    @DisplayName("Non-staff cannot add record")
    void testNonStaffCannotAddRecord() {
        RepairProcessRecordRequest request = new RepairProcessRecordRequest(
            RepairProcessActionType.ARRIVED,
            "测试",
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertThrows(BusinessException.class, () ->
            processRecordService.addRecord(ticketId, studentId, request));
    }

    // ==================== Helper Methods ====================

    private Long createTestTicket(String description) {
        com.qiyun.repairservice.dto.request.TicketCreateRequest request =
            new com.qiyun.repairservice.dto.request.TicketCreateRequest(
                studentId,
                categoryId,
                "测试地点",
                description,
                "medium",
                List.of("test.jpg")
            );
        com.qiyun.repairservice.dto.TicketDetailDto dto = ticketService.createTicket(request, null);
        return dto.ticketId();
    }
}