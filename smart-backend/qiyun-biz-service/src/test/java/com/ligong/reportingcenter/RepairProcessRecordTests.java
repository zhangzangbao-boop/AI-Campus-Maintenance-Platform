package com.ligong.reportingcenter;

import static org.assertj.core.api.Assertions.assertThat;

import com.ligong.reportingcenter.domain.enums.RepairProcessActionType;
import com.ligong.reportingcenter.domain.enums.TicketStatus;
import com.ligong.reportingcenter.domain.enums.UserRole;
import com.ligong.reportingcenter.dto.CategoryDto;
import com.ligong.reportingcenter.dto.RepairProcessRecordDto;
import com.ligong.reportingcenter.dto.TicketDetailDto;
import com.ligong.reportingcenter.dto.request.RepairProcessRecordRequest;
import com.ligong.reportingcenter.dto.request.TicketCreateRequest;
import com.ligong.reportingcenter.dto.request.UserRegisterRequest;
import com.ligong.reportingcenter.service.AiAnalysisIntegrationService;
import com.ligong.reportingcenter.service.CategoryService;
import com.ligong.reportingcenter.service.RepairProcessRecordService;
import com.ligong.reportingcenter.service.TicketService;
import com.ligong.reportingcenter.service.UserService;
import com.qiyun.feign.client.AiServiceClient;
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
 * 维修过程记录测试类
 *
 * 测试新增字段：到场时间、维修描述、使用材料、完成时间
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepairProcessRecordTests {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private RepairProcessRecordService processRecordService;

    @MockBean
    private AiServiceClient aiServiceClient;

    @MockBean
    private AiAnalysisIntegrationService aiAnalysisIntegrationService;

    private String studentId;
    private String staffId;
    private Long categoryId;
    private Long ticketId;

    @BeforeEach
    void setUp() {
        studentId = "process_test_student";
        staffId = "process_test_staff";

        // 注册测试用户
        try {
            userService.register(new UserRegisterRequest(studentId, "123456", "测试学生", "13800000001", UserRole.STUDENT));
        } catch (Exception ignored) {}
        try {
            userService.register(new UserRegisterRequest(staffId, "123456", "测试维修工", "13800000002", UserRole.STAFF));
        } catch (Exception ignored) {}

        // 获取测试分类
        List<CategoryDto> categories = categoryService.listAll();
        if (categories == null || categories.isEmpty()) {
            CategoryDto created = categoryService.create("测试分类");
            categoryId = created.categoryId();
        } else {
            categoryId = categories.get(0).categoryId();
        }

        // 创建测试工单
        ticketId = createTestTicket("测试工单");
        // 接单
        ticketService.acceptTicket(ticketId, staffId);
    }

    @Test
    @DisplayName("完整流程：到场→维修→完成")
    void testCompleteProcessFlow() {
        System.out.println("\n========== 测试维修过程记录完整流程 ==========");

        // 1. 到场确认
        System.out.println("\n步骤1: 维修工到场确认");
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
        System.out.println("✅ 到场记录已保存");
        System.out.println("   - 到场时间: " + arrivedRecord.arrivedAt());
        System.out.println("   - 内容: " + arrivedRecord.content());

        // 2. 记录维修过程
        System.out.println("\n步骤2: 记录维修过程");
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
        System.out.println("✅ 维修记录已保存");
        System.out.println("   - 维修描述: " + repairRecord.repairDescription());
        System.out.println("   - 使用材料: " + repairRecord.materialsUsed());
        System.out.println("   - 耗时: " + repairRecord.durationMinutes() + "分钟");

        // 3. 完成确认
        System.out.println("\n步骤3: 维修完成确认");
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
        System.out.println("✅ 完成记录已保存");
        System.out.println("   - 完成时间: " + finishRecord.finishedAt());
        System.out.println("   - 备注: " + finishRecord.remarks());

        // 4. 查询所有记录
        System.out.println("\n步骤4: 查询维修过程记录列表");
        List<RepairProcessRecordDto> records = processRecordService.listRecords(ticketId, staffId);
        assertThat(records).hasSize(3);

        System.out.println("✅ 共查询到 " + records.size() + " 条记录");
        for (RepairProcessRecordDto record : records) {
            System.out.println("   - [" + record.actionType().getDescription() + "] " + record.content());
        }

        System.out.println("\n========== 维修过程记录测试通过 ==========\n");
    }

    @Test
    @DisplayName("到场确认：自动设置到场时间")
    void testArrivedRecordAutoTime() {
        RepairProcessRecordDto record = processRecordService.recordArrival(
            ticketId,
            staffId,
            "已到达现场",
            null
        );

        assertThat(record.actionType()).isEqualTo(RepairProcessActionType.ARRIVED);
        assertThat(record.arrivedAt()).isNotNull();
        System.out.println("✅ 到场时间自动设置成功");
    }

    @Test
    @DisplayName("维修记录：使用材料JSON格式")
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
        System.out.println("✅ 材料使用记录成功");
        System.out.println("   - 材料: " + record.materialsUsed());
    }

    @Test
    @DisplayName("完成记录：自动设置完成时间")
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
        System.out.println("✅ 完成时间自动设置成功");
    }

    @Test
    @DisplayName("快捷方法：recordArrival")
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
        System.out.println("✅ recordArrival 快捷方法测试通过");
    }

    @Test
    @DisplayName("快捷方法：recordRepairProcess")
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
        System.out.println("✅ recordRepairProcess 快捷方法测试通过");
    }

    @Test
    @DisplayName("快捷方法：recordFinish")
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
        System.out.println("✅ recordFinish 快捷方法测试通过");
    }

    @Test
    @DisplayName("查询记录：按时间升序排列")
    void testListRecordsOrdered() throws InterruptedException {
        // 添加多条记录
        processRecordService.recordArrival(ticketId, staffId, "到场", null);
        Thread.sleep(100); // 确保时间不同
        processRecordService.recordRepairProcess(ticketId, staffId, "维修中", null, null, null);
        Thread.sleep(100);
        processRecordService.recordFinish(ticketId, staffId, "完成", null, null, null);

        List<RepairProcessRecordDto> records = processRecordService.listRecords(ticketId, staffId);

        // 验证按时间升序排列
        for (int i = 1; i < records.size(); i++) {
            assertThat(records.get(i).createdAt())
                .isAfterOrEqualTo(records.get(i - 1).createdAt());
        }

        System.out.println("✅ 记录按时间升序排列验证通过");
    }

    // ==================== 辅助方法 ====================

    private Long createTestTicket(String description) {
        TicketCreateRequest request = new TicketCreateRequest(
            studentId,
            categoryId,
            "测试地点",
            description,
            "medium",
            List.of("test.jpg")
        );
        TicketDetailDto dto = ticketService.createTicket(request);
        return dto.ticketId();
    }
}