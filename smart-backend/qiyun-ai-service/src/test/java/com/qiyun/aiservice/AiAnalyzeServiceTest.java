package com.qiyun.aiservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.aiservice.config.DeepSeekConfig;
import com.qiyun.aiservice.service.AiAnalyzeService;
import com.qiyun.aiservice.service.AiRuleConfigService;
import com.qiyun.aiservice.service.DeepSeekClientService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AI 分析服务测试
 */
@ExtendWith(MockitoExtension.class)
class AiAnalyzeServiceTest {

    private AiAnalyzeService aiAnalyzeService;
    private DeepSeekClientService deepSeekClientService;
    private DeepSeekConfig config;
    private AiRuleConfigService aiRuleConfigService;

    @BeforeEach
    void setUp() {
        // 创建配置（AI 未启用）
        config = new DeepSeekConfig();
        config.setEnabled(false);

        // 创建服务
        ObjectMapper objectMapper = new ObjectMapper();
        deepSeekClientService = new DeepSeekClientService(config, objectMapper);
        aiRuleConfigService = mock(AiRuleConfigService.class);
        lenient().when(aiRuleConfigService.rules()).thenReturn(new AiRuleConfigService.AiRules(
            List.of(
                new AiRuleConfigService.KeywordRule("空调故障", List.of("空调", "制冷", "制热", "风机", "遥控器")),
                new AiRuleConfigService.KeywordRule("管道故障", List.of("漏水", "滴水", "水管", "水龙头", "下水", "地漏", "积水", "堵塞")),
                new AiRuleConfigService.KeywordRule("电力故障", List.of("断电", "跳闸", "插座", "电路", "电线", "开关", "照明", "灯", "频闪")),
                new AiRuleConfigService.KeywordRule("网络故障", List.of("网络", "wifi", "无线", "网口", "断线", "校园网", "无法连接")),
                new AiRuleConfigService.KeywordRule("家具故障", List.of("桌", "椅", "床", "柜", "门锁", "家具")),
                new AiRuleConfigService.KeywordRule("门窗故障", List.of("门", "窗", "玻璃", "闭门器"))
            ),
            List.of(
                new AiRuleConfigService.KeywordRule("高", List.of("触电", "漏电", "烧焦", "冒烟", "火花", "火灾", "被困", "大范围停电", "大范围停水", "严重漏水")),
                new AiRuleConfigService.KeywordRule("中", List.of("无法使用", "不能用", "断线", "频闪", "损坏", "不制冷", "堵塞", "松动", "脱落", "漏水", "滴水"))
            )
        ));
        aiAnalyzeService = new AiAnalyzeService(deepSeekClientService, aiRuleConfigService);
    }

    @Test
    @DisplayName("规则引擎：空调故障识别")
    void testAirConditionerCategory() {
        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "宿舍空调不制冷，遥控器没反应",
            "1号楼203"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("空调故障", response.category());
        assertNotNull(response.urgency());
        assertNotNull(response.suggestion());
        assertFalse(response.keywords().isEmpty());
    }

    @Test
    @DisplayName("规则引擎：管道故障识别")
    void testPlumbingCategory() {
        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "卫生间水管漏水，地漏堵塞",
            "2号楼305"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("管道故障", response.category());
        assertEquals("中", response.urgency());
        assertTrue(response.suggestion().contains("阀门") || response.suggestion().contains("检查"));
    }

    @Test
    @DisplayName("规则引擎：电力故障识别")
    void testElectricalCategory() {
        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "宿舍插座没电，灯管频闪",
            "3号楼101"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("电力故障", response.category());
        assertNotNull(response.urgency());
        assertFalse(response.keywords().isEmpty());
    }

    @Test
    @DisplayName("规则引擎：网络故障识别")
    void testNetworkCategory() {
        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "校园网无法连接，WiFi信号很弱",
            "图书馆201"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("网络故障", response.category());
        assertNotNull(response.suggestion());
    }

    @Test
    @DisplayName("规则引擎：家具故障识别")
    void testFurnitureCategory() {
        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "宿舍椅子靠背松动，桌子腿断了",
            "5号楼402"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("家具故障", response.category());
    }

    @Test
    @DisplayName("规则引擎：门窗故障识别")
    void testDoorWindowCategory() {
        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "宿舍窗户玻璃裂了，闭门器损坏",
            "6号楼501"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("门窗故障", response.category());
        assertEquals("中", response.urgency());
    }

    @Test
    @DisplayName("规则引擎：其他故障分类")
    void testOtherCategory() {
        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "墙皮脱落，需要粉刷",
            "7号楼走廊"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("其他故障", response.category());
        assertNotNull(response.suggestion());
    }

    @Test
    @DisplayName("紧急程度判断：安全相关问题")
    void testHighUrgency() {
        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "电线冒烟有烧焦味道",
            "配电间"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("高", response.urgency());
        assertNotNull(response.suggestion());
    }

    @Test
    @DisplayName("紧急程度判断：普通问题")
    void testMediumUrgency() {
        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "空调不制冷，无法使用",
            "普通"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("中", response.urgency());
    }

    @Test
    @DisplayName("紧急程度判断：一般问题")
    void testLowUrgency() {
        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "墙皮有轻微污渍",
            "一般"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("低", response.urgency());
    }

    @Test
    @DisplayName("紧急程度判断：典型和易误判中文场景")
    void urgencyRulesHandleTypicalAndAmbiguousCases() {
        assertUrgency("电线冒烟并有烧焦味，旁边有人经过", "实验楼配电箱", "高");
        assertUrgency("三号宿舍楼整层停电，走廊和房间都没电", "三号宿舍楼", "高");
        assertUrgency("宿舍卫生间严重漏水，水已经漫到插座附近", "2号楼305", "高");
        assertUrgency("电梯门打不开，有同学被困在里面", "教学楼一层", "高");
        assertUrgency("空调不制冷，晚上休息和学习都受影响", "5号楼402", "中");
        assertUrgency("校园网无法连接，整间自习室不能上网", "图书馆201", "中");
        assertUrgency("只说设备有点问题，没有说明影响范围", "", "中");
        assertUrgency("门把手轻微松动但还能正常使用，不急", "宿舍门口", "低");
        assertUrgency("墙面有少量污渍，只是外观问题，不影响正常使用", "7号楼走廊", "低");
    }

    @Test
    @DisplayName("DeepSeek 结果：安全硬规则优先并限制为高/中/低")
    void deepSeekUrgencyIsCalibratedBySafetyRules() {
        DeepSeekClientService mockedDeepSeek = mock(DeepSeekClientService.class);
        when(mockedDeepSeek.isAvailable()).thenReturn(true);
        when(mockedDeepSeek.analyzeTicket(anyString(), anyString())).thenReturn(Map.of(
            "category", "电力故障",
            "urgency", "低",
            "suggestion", "建议尽快处理",
            "keywords", List.of("电线", "冒烟")
        ));
        AiAnalyzeService service = new AiAnalyzeService(mockedDeepSeek, aiRuleConfigService);

        var response = service.analyze(new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "电线冒烟，有明显烧焦味",
            "实验楼配电间"
        ));

        assertNotNull(response);
        assertEquals("高", response.urgency());
        assertTrue(List.of("高", "中", "低").contains(response.urgency()));
        assertEquals("deepseek", response.source());
    }

    @Test
    @DisplayName("DeepSeek 结果：非安全高风险不得误升高")
    void deepSeekHighWithoutSafetyRiskIsDowngraded() {
        DeepSeekClientService mockedDeepSeek = mock(DeepSeekClientService.class);
        when(mockedDeepSeek.isAvailable()).thenReturn(true);
        when(mockedDeepSeek.analyzeTicket(anyString(), anyString())).thenReturn(Map.of(
            "category", "门窗故障",
            "urgency", "高",
            "suggestion", "建议安排维修",
            "keywords", List.of("划痕", "门")
        ));
        AiAnalyzeService service = new AiAnalyzeService(mockedDeepSeek, aiRuleConfigService);

        var response = service.analyze(new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "宿舍门表面有轻微划痕，不影响正常使用",
            "5号楼402"
        ));

        assertNotNull(response);
        assertEquals("低", response.urgency());
    }

    @Test
    @DisplayName("关键词提取")
    void testKeywordExtraction() {
        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "宿舍空调漏水，网络断线",
            "测试位置"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertNotNull(response.keywords());
        // 验证关键词包含核心问题词
        String allKeywords = response.keywords().toString();
        assertTrue(allKeywords.contains("空调") || allKeywords.contains("漏水") || allKeywords.contains("网络"));
    }

    @Test
    @DisplayName("DeepSeek 未配置时降级到规则引擎")
    void testFallbackToRuleEngine() {
        // 配置未启用
        config.setEnabled(false);
        config.setApiKey(null);

        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "空调不制冷",
            "测试位置"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("空调故障", response.category());
    }

    @Test
    @DisplayName("配置化规则动态影响分类")
    void configurableRulesAffectCategory() {
        when(aiRuleConfigService.rules()).thenReturn(new AiRuleConfigService.AiRules(
            List.of(new AiRuleConfigService.KeywordRule("墙面故障", List.of("墙皮"))),
            List.of(new AiRuleConfigService.KeywordRule("中", List.of("脱落")))
        ));

        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "墙皮脱落，需要粉刷",
            "7号楼走廊"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("墙面故障", response.category());
        assertEquals("中", response.urgency());
    }

    @Test
    @DisplayName("规则引擎：识别报修标题和具体位置")
    void ruleEngineExtractsTitleAndLocation() {
        var request = new com.qiyun.aiservice.dto.AnalyzeTicketRequest(
            "三号宿舍楼 6-612 的照明灯一直频闪，晚上学习很受影响，担心线路有问题。",
            ""
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("电力故障", response.category());
        assertEquals("三号宿舍楼 6-612", response.locationText());
        assertEquals("三号宿舍楼 6-612", response.location());
        assertTrue(response.title().contains("三号宿舍楼 6-612"));
        assertTrue(response.title().contains("照明故障"));
        assertEquals("rules", response.source());
    }

    @Test
    @DisplayName("维修报告：DeepSeek 未启用时生成规则兜底报告")
    void generateRepairReportFallsBackToRules() {
        String report = aiAnalyzeService.generateRepairReport(
            "三号宿舍楼 6-612 的照明灯一直频闪",
            "已更换灯管并检查线路，现场测试照明恢复正常。"
        );

        assertNotNull(report);
        assertTrue(report.contains("维修处理报告"));
        assertTrue(report.contains("已更换灯管"));
        assertTrue(report.contains("基础功能测试"));
    }


    @Test
    @DisplayName("???????????????")
    void testFeedbackSentimentFallback() {
        var request = new com.qiyun.aiservice.dto.AnalyzeFeedbackSentimentRequest(
            "\u7b49\u5f85\u592a\u6162\uff0c\u95ee\u9898\u4ecd\u7136\u6ca1\u89e3\u51b3\uff0c\u6001\u5ea6\u4e5f\u4e0d\u597d"
        );

        var response = aiAnalyzeService.analyzeFeedbackSentiment(request);

        assertNotNull(response);
        assertEquals("NEGATIVE", response.sentiment());
        assertTrue(response.score() >= 0 && response.score() <= 1);
        assertFalse(response.keywords().isEmpty());
        assertNotNull(response.summary());
    }

    private void assertUrgency(String description, String location, String expectedUrgency) {
        var response = aiAnalyzeService.analyze(new com.qiyun.aiservice.dto.AnalyzeTicketRequest(description, location));
        assertNotNull(response);
        assertEquals(expectedUrgency, response.urgency(), description);
        assertTrue(List.of("高", "中", "低").contains(response.urgency()));
    }

}
