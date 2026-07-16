package com.qiyun.aiservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.aiservice.config.DeepSeekConfig;
import com.qiyun.aiservice.service.AiAnalyzeService;
import com.qiyun.aiservice.service.DeepSeekClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 分析服务测试
 */
@ExtendWith(MockitoExtension.class)
class AiAnalyzeServiceTest {

    private AiAnalyzeService aiAnalyzeService;
    private DeepSeekClientService deepSeekClientService;
    private DeepSeekConfig config;

    @BeforeEach
    void setUp() {
        // 创建配置（AI 未启用）
        config = new DeepSeekConfig();
        config.setEnabled(false);

        // 创建服务
        ObjectMapper objectMapper = new ObjectMapper();
        deepSeekClientService = new DeepSeekClientService(config, objectMapper);
        aiAnalyzeService = new AiAnalyzeService(deepSeekClientService);
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
        assertEquals("紧急", response.urgency()); // 漏水属于紧急
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
        assertEquals("紧急", response.urgency()); // 玻璃破裂属于紧急
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
            "紧急"
        );

        var response = aiAnalyzeService.analyze(request);

        assertNotNull(response);
        assertEquals("紧急", response.urgency());
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
        assertEquals("普通", response.urgency());
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
        assertEquals("一般", response.urgency());
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
}