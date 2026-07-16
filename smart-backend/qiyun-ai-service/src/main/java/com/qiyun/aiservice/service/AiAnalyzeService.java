package com.qiyun.aiservice.service;

import com.qiyun.aiservice.dto.AnalyzeTicketRequest;
import com.qiyun.aiservice.dto.AnalyzeTicketResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 工单分析服务
 * 优先调用 DeepSeek API，失败时降级到规则引擎
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalyzeService {

    private final DeepSeekClientService deepSeekClientService;

    /**
     * 分析工单描述
     */
    public AnalyzeTicketResponse analyze(AnalyzeTicketRequest request) {
        String description = request.description().toLowerCase(Locale.ROOT);
        String location = request.location();

        // 尝试调用 DeepSeek API
        if (deepSeekClientService.isAvailable()) {
            log.info("尝试调用 DeepSeek AI 进行分析");
            try {
                AnalyzeTicketResponse aiResponse = analyzeByDeepSeek(description, location);
                if (aiResponse != null) {
                    log.info("DeepSeek AI 分析成功: category={}, urgency={}",
                        aiResponse.category(), aiResponse.urgency());
                    return aiResponse;
                }
                log.warn("DeepSeek AI 返回空结果，降级到规则引擎");
            } catch (Exception e) {
                log.error("DeepSeek AI 调用失败，降级到规则引擎: {}", e.getMessage());
            }
        } else {
            log.debug("DeepSeek AI 未启用或未配置，使用规则引擎");
        }

        // 降级到规则引擎
        return analyzeByRules(description);
    }

    /**
     * 使用 DeepSeek API 分析
     */
    private AnalyzeTicketResponse analyzeByDeepSeek(String description, String location) {
        Map<String, Object> result = deepSeekClientService.analyzeTicket(description, location);
        if (result == null || result.isEmpty()) {
            return null;
        }

        try {
            String category = getStringValue(result, "category");
            String urgency = getStringValue(result, "urgency");
            String suggestion = getStringValue(result, "suggestion");
            List<String> keywords = getStringListValue(result, "keywords");

            // 验证必要字段
            if (category == null || category.isBlank()) {
                category = "其他故障";
            }
            if (urgency == null || urgency.isBlank()) {
                urgency = "一般";
            }
            if (suggestion == null || suggestion.isBlank()) {
                suggestion = "建议现场检查后确定故障原因";
            }
            if (keywords == null || keywords.isEmpty()) {
                keywords = extractKeywords(description);
            }

            return new AnalyzeTicketResponse(category, urgency, suggestion, keywords);
        } catch (Exception e) {
            log.error("解析 DeepSeek 响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用规则引擎分析（降级方案）
     */
    private AnalyzeTicketResponse analyzeByRules(String description) {
        // 1. 根据关键词判断故障分类
        String category = determineCategory(description);

        // 2. 根据关键词判断紧急程度
        String urgency = determineUrgency(description);

        // 3. 生成维修建议
        String suggestion = generateSuggestion(category, description);

        // 4. 提取关键词
        List<String> keywords = extractKeywords(description);

        return new AnalyzeTicketResponse(category, urgency, suggestion, keywords);
    }

    /**
     * 从 Map 中获取字符串值
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * 从 Map 中获取字符串列表
     */
    @SuppressWarnings("unchecked")
    private List<String> getStringListValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item).trim());
                }
            }
            return result;
        }
        return null;
    }

    /**
     * 根据关键词判断故障分类
     */
    private String determineCategory(String description) {
        // 空调相关
        if (containsAny(description, "空调", "制冷", "制热", "风机", "遥控器")) {
            return "空调故障";
        }

        // 漏水/管道相关
        if (containsAny(description, "漏水", "滴水", "水管", "水龙头", "下水", "地漏", "积水", "堵塞")) {
            return "管道故障";
        }

        // 电力相关
        if (containsAny(description, "断电", "跳闸", "插座", "电路", "电线", "开关", "照明", "灯", "频闪")) {
            return "电力故障";
        }

        // 网络相关
        if (containsAny(description, "网络", "wifi", "无线", "网口", "断线", "校园网", "无法连接")) {
            return "网络故障";
        }

        // 家具相关
        if (containsAny(description, "桌", "椅", "床", "柜", "门锁", "家具")) {
            return "家具故障";
        }

        // 门窗相关
        if (containsAny(description, "门", "窗", "玻璃", "闭门器")) {
            return "门窗故障";
        }

        // 默认分类
        return "其他故障";
    }

    /**
     * 根据关键词判断紧急程度
     */
    private String determineUrgency(String description) {
        // 高紧急度 - 安全相关
        if (containsAny(description, "漏水", "积水", "触电", "烧焦", "冒烟", "火花",
                        "异味", "总闸", "消防", "灭火器", "玻璃", "危险")) {
            return "紧急";
        }

        // 中紧急度 - 影响使用
        if (containsAny(description, "无法使用", "不能用", "断线", "频闪", "损坏",
                        "不制冷", "堵塞", "松动", "脱落")) {
            return "普通";
        }

        // 低紧急度 - 一般问题
        return "一般";
    }

    /**
     * 生成维修建议
     */
    private String generateSuggestion(String category, String description) {
        switch (category) {
            case "空调故障":
                if (description.contains("制冷")) {
                    return "建议检查制冷模块、电源连接情况，确认滤网是否需要清洗";
                }
                if (description.contains("制热")) {
                    return "建议检查制热模块、温控设置，确认室外机工作状态";
                }
                return "建议检查空调电源、遥控器设置，确认室内外机运行状态";

            case "管道故障":
                if (description.contains("漏水") || description.contains("滴水")) {
                    return "建议关闭相关阀门，检查水管接口和密封件，必要时更换配件";
                }
                if (description.contains("堵塞") || description.contains("下水")) {
                    return "建议检查堵塞位置，使用疏通工具或联系专业疏通服务";
                }
                return "建议检查管道连接处，确认漏水点和损坏程度";

            case "电力故障":
                if (description.contains("断电") || description.contains("跳闸")) {
                    return "建议检查电路负载，确认是否过载或短路，必要时联系电工";
                }
                if (description.contains("照明") || description.contains("灯")) {
                    return "建议检查灯管、镇流器和开关状态，必要时更换配件";
                }
                if (description.contains("插座")) {
                    return "建议检查插座接线、电源线路，确认是否存在接触不良";
                }
                return "建议检查电路和开关状态，确认故障原因";

            case "网络故障":
                if (description.contains("wifi") || description.contains("无线")) {
                    return "建议检查无线信号强度，确认路由器和接入点工作状态";
                }
                if (description.contains("网口") || description.contains("断线")) {
                    return "建议检查网线和网口连接，必要时更换水晶头或网线";
                }
                return "建议检查网络设备连接状态，确认账号和密码配置";

            case "家具故障":
                return "建议检查家具结构稳定性，加固或更换损坏配件";

            case "门窗故障":
                if (description.contains("玻璃")) {
                    return "建议隔离破损区域，防止划伤，等待专业人员更换玻璃";
                }
                if (description.contains("门锁")) {
                    return "建议检查门锁机械结构和钥匙匹配情况，必要时更换锁芯";
                }
                return "建议检查门窗闭合机构，确认铰链和把手状态";

            default:
                return "建议保留现场照片并补充准确位置，等待维修人员现场检查";
        }
    }

    /**
     * 提取描述中的关键词
     */
    private List<String> extractKeywords(String description) {
        List<String> keywords = new ArrayList<>();

        // 设备关键词
        if (description.contains("空调")) keywords.add("空调");
        if (description.contains("水管") || description.contains("水龙头")) keywords.add("水管");
        if (description.contains("灯") || description.contains("照明")) keywords.add("照明");
        if (description.contains("插座")) keywords.add("插座");
        if (description.contains("网络") || description.contains("wifi")) keywords.add("网络");
        if (description.contains("桌") || description.contains("椅")) keywords.add("家具");
        if (description.contains("门") || description.contains("窗")) keywords.add("门窗");

        // 状态关键词
        if (description.contains("漏水") || description.contains("滴水")) keywords.add("漏水");
        if (description.contains("断电") || description.contains("跳闸")) keywords.add("断电");
        if (description.contains("制冷")) keywords.add("制冷");
        if (description.contains("损坏") || description.contains("坏了")) keywords.add("损坏");
        if (description.contains("堵塞")) keywords.add("堵塞");

        // 如果没有提取到关键词，添加通用关键词
        if (keywords.isEmpty()) {
            keywords.add("设备");
            keywords.add("故障");
        }

        return keywords;
    }

    /**
     * 检查文本是否包含任意关键词
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}