package com.qiyun.aiservice.service;

import com.qiyun.aiservice.dto.AnalyzeFeedbackSentimentRequest;
import com.qiyun.aiservice.dto.AnalyzeFeedbackSentimentResponse;
import com.qiyun.aiservice.dto.AnalyzeTicketRequest;
import com.qiyun.aiservice.dto.AnalyzeTicketResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AI 工单分析服务
 * 优先调用 DeepSeek API，失败时降级到规则引擎
 */
@Slf4j
@Service
public class AiAnalyzeService {

    private final DeepSeekClientService deepSeekClientService;
    private final AiRuleConfigService aiRuleConfigService;

    @Autowired
    public AiAnalyzeService(DeepSeekClientService deepSeekClientService, AiRuleConfigService aiRuleConfigService) {
        this.deepSeekClientService = deepSeekClientService;
        this.aiRuleConfigService = aiRuleConfigService;
    }

    public AiAnalyzeService(DeepSeekClientService deepSeekClientService) {
        this(deepSeekClientService, new AiRuleConfigService(new ObjectMapper()));
    }

    /**
     * 分析工单描述
     */
    public AnalyzeTicketResponse analyze(AnalyzeTicketRequest request) {
        String rawDescription = request.description() == null ? "" : request.description().trim();
        String description = rawDescription.toLowerCase(Locale.ROOT);
        String location = cleanText(request.location());

        // 尝试调用 DeepSeek API
        if (deepSeekClientService.isAvailable()) {
            log.info("尝试调用 DeepSeek AI 进行分析");
            try {
                AnalyzeTicketResponse aiResponse = analyzeByDeepSeek(rawDescription, location);
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
        return analyzeByRules(description, rawDescription, location);
    }

    public AnalyzeFeedbackSentimentResponse analyzeFeedbackSentiment(AnalyzeFeedbackSentimentRequest request) {
        String comment = request.comment() == null ? "" : request.comment().trim();

        if (deepSeekClientService.isAvailable()) {
            try {
                Map<String, Object> result = deepSeekClientService.analyzeFeedbackSentiment(comment);
                AnalyzeFeedbackSentimentResponse response = parseSentimentResponse(result, comment);
                if (response != null) {
                    return response;
                }
            } catch (Exception e) {
                log.warn("DeepSeek feedback sentiment unavailable, fallback to rules: {}", e.getMessage());
            }
        }

        return analyzeFeedbackByRules(comment);
    }

    public String generateRepairReport(String description, String processNotes) {
        String problem = cleanText(description);
        String process = cleanText(processNotes);
        if (deepSeekClientService.isAvailable()) {
            String aiReport = deepSeekClientService.requestText(
                "你是校园后勤维修工单助手。请用中文生成简洁、规范、可直接提交的维修处理报告，不要编造不存在的耗材和结果。",
                "问题描述：" + problem + "\n维修过程记录：" + process + "\n请输出 120 字以内的维修报告。",
                400
            );
            if (!aiReport.isBlank()) {
                return aiReport;
            }
        }
        if (process.isBlank()) {
            process = "已完成现场检查、故障定位和必要维修处理。";
        }
        return "维修处理报告：针对“" + shortText(problem, 80) + "”，维修人员已进行现场核查。"
            + process
            + " 处理后已完成基础功能测试，建议后续继续观察同位置是否出现重复故障。";
    }

    private AnalyzeFeedbackSentimentResponse parseSentimentResponse(Map<String, Object> result, String comment) {
        if (result == null || result.isEmpty()) {
            return null;
        }
        String sentiment = normalizeSentiment(getStringValue(result, "sentiment"), comment);
        Double score = getDoubleValue(result, "score");
        List<String> keywords = getStringListValue(result, "keywords");
        String summary = getStringValue(result, "summary");
        if (score == null) {
            score = ruleScore(sentiment, comment);
        }
        if (keywords == null || keywords.isEmpty()) {
            keywords = extractFeedbackKeywords(comment);
        }
        if (summary == null || summary.isBlank()) {
            summary = buildFeedbackSummary(sentiment);
        }
        return new AnalyzeFeedbackSentimentResponse(sentiment, clamp(score), keywords, summary);
    }

    private AnalyzeFeedbackSentimentResponse analyzeFeedbackByRules(String comment) {
        String sentiment = normalizeSentiment(null, comment);
        return new AnalyzeFeedbackSentimentResponse(
            sentiment,
            ruleScore(sentiment, comment),
            extractFeedbackKeywords(comment),
            buildFeedbackSummary(sentiment)
        );
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
            String locationText = firstNonBlank(
                getStringValue(result, "locationText"),
                getStringValue(result, "location"),
                extractLocation(description, location)
            );

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
                keywords = extractKeywords(description.toLowerCase(Locale.ROOT));
            }
            String title = firstNonBlank(
                getStringValue(result, "title"),
                buildTitle(description, category, locationText)
            );

            return new AnalyzeTicketResponse(category, urgency, suggestion, keywords, title, locationText, locationText, "deepseek");
        } catch (Exception e) {
            log.error("解析 DeepSeek 响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用规则引擎分析（降级方案）
     */
    private AnalyzeTicketResponse analyzeByRules(String description, String rawDescription, String providedLocation) {
        // 1. 根据关键词判断故障分类
        String category = determineCategory(description);

        // 2. 根据关键词判断紧急程度
        String urgency = determineUrgency(description);

        // 3. 生成维修建议
        String suggestion = generateSuggestion(category, description);

        // 4. 提取关键词
        List<String> keywords = extractKeywords(description);
        String locationText = extractLocation(rawDescription, providedLocation);
        String title = buildTitle(rawDescription, category, locationText);

        return new AnalyzeTicketResponse(category, urgency, suggestion, keywords, title, locationText, locationText, "rules");
    }

    /**
     * 从 Map 中获取字符串值
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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

    private String normalizeSentiment(String sentiment, String comment) {
        if (sentiment != null) {
            String normalized = sentiment.trim().toUpperCase(Locale.ROOT);
            if ("POSITIVE".equals(normalized) || "NEUTRAL".equals(normalized) || "NEGATIVE".equals(normalized)) {
                return normalized;
            }
        }
        String text = comment == null ? "" : comment.toLowerCase(Locale.ROOT);
        int positive = countContains(text, "\u6ee1\u610f", "\u53ca\u65f6", "\u5f88\u5feb", "\u4e13\u4e1a", "\u8d1f\u8d23", "\u8010\u5fc3", "\u89e3\u51b3", "\u5e72\u51c0", "\u4e0d\u9519", "\u5f88\u597d", "\u597d\u8bc4", "\u611f\u8c22");
        int negative = countContains(text, "\u4e0d\u6ee1\u610f", "\u592a\u6162", "\u5f88\u6162", "\u62d6\u5ef6", "\u6577\u884d", "\u6ca1\u89e3\u51b3", "\u672a\u89e3\u51b3", "\u5dee", "\u6295\u8bc9", "\u6001\u5ea6\u4e0d\u597d", "\u7cdf\u7cd5", "\u4ecd\u7136", "\u518d\u6b21\u574f");
        if (negative > positive) {
            return "NEGATIVE";
        }
        if (positive > negative) {
            return "POSITIVE";
        }
        return "NEUTRAL";
    }

    private double ruleScore(String sentiment, String comment) {
        String text = comment == null ? "" : comment;
        int strong = countContains(text, "\u975e\u5e38", "\u7279\u522b", "\u5f88", "\u592a", "\u4e25\u91cd", "\u5b8c\u5168", "\u4e00\u76f4");
        double base = switch (sentiment) {
            case "POSITIVE" -> 0.78;
            case "NEGATIVE" -> 0.82;
            default -> 0.55;
        };
        return clamp(base + Math.min(strong, 2) * 0.08);
    }

    private List<String> extractFeedbackKeywords(String comment) {
        List<String> keywords = new ArrayList<>();
        String text = comment == null ? "" : comment;
        addKeywordIfPresent(keywords, text, "及时", "快", "很快", "及时");
        addKeywordIfPresent(keywords, text, "态度", "态度", "耐心", "负责", "敷衍");
        addKeywordIfPresent(keywords, text, "质量", "专业", "干净", "质量", "返修");
        addKeywordIfPresent(keywords, text, "未解决", "没解决", "未解决", "仍然", "再次坏");
        addKeywordIfPresent(keywords, text, "等待", "等", "慢", "拖延");
        addKeywordIfPresent(keywords, text, "满意", "满意", "好评", "感谢", "不错");
        if (keywords.isEmpty()) {
            keywords.add("服务反馈");
        }
        return keywords.stream().distinct().limit(5).toList();
    }

    private void addKeywordIfPresent(List<String> keywords, String text, String label, String... matches) {
        for (String match : matches) {
            if (text.contains(match)) {
                keywords.add(label);
                return;
            }
        }
    }

    private String buildFeedbackSummary(String sentiment) {
        return switch (sentiment) {
            case "POSITIVE" -> "学生反馈整体满意，维修服务体验较好。";
            case "NEGATIVE" -> "学生反馈存在不满，建议管理员跟进处理质量和沟通情况。";
            default -> "学生反馈较为中性，可作为常规服务记录参考。";
        };
    }

    private int countContains(String text, String... words) {
        int count = 0;
        for (String word : words) {
            if (text.contains(word.toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, Math.round(value * 100.0) / 100.0));
    }

    /**
     * 根据关键词判断故障分类
     */
    private String determineCategory(String description) {
        for (AiRuleConfigService.KeywordRule rule : aiRuleConfigService.rules().categoryRules()) {
            if (containsAny(description, rule.keywords().toArray(String[]::new))) {
                return rule.result();
            }
        }

        // 默认分类
        return "其他故障";
    }

    /**
     * 根据关键词判断紧急程度
     */
    private String determineUrgency(String description) {
        for (AiRuleConfigService.KeywordRule rule : aiRuleConfigService.rules().urgencyRules()) {
            if (containsAny(description, rule.keywords().toArray(String[]::new))) {
                return rule.result();
            }
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

    private String extractLocation(String description, String providedLocation) {
        String directLocation = cleanText(providedLocation);
        if (!directLocation.isBlank()) {
            return directLocation;
        }

        String text = cleanText(description);
        if (text.isBlank()) {
            return "";
        }

        String[] regexes = {
            "([一二三四五六七八九十0-9]+号?(?:宿舍楼|宿舍|公寓|教学楼|实验楼|楼|栋|幢)\\s*\\d{1,2}[-—]\\d{2,4})",
            "((?:宿舍楼|宿舍|公寓|教学楼|实验楼|图书馆|食堂|体育馆)\\s*\\d+\\s*楼\\s*(?:卫生间|洗手间|浴室|走廊|大厅|教室|实验室|房间|宿舍)?)",
            "([一二三四五六七八九十0-9]+号?(?:宿舍楼|公寓|教学楼|实验楼|楼|栋|幢)\\s*\\d{2,4}(?:室|房间|宿舍)?)",
            "((?:东|西|南|北)?(?:区)?[A-Za-z0-9一二三四五六七八九十]+(?:栋|幢|号楼|楼)\\s*\\d{2,4}(?:室|房间|宿舍)?)",
            "((?:图书馆|食堂|教学楼|实验楼|体育馆|操场|宿舍楼|宿舍|公寓)[\\u4e00-\\u9fa5A-Za-z0-9\\s\\-—]{0,16}(?:卫生间|洗手间|浴室|走廊|大厅|教室|实验室|门口|入口|房间|宿舍))"
        };

        for (String regex : regexes) {
            Matcher matcher = Pattern.compile(regex).matcher(text);
            if (matcher.find()) {
                return cleanLocation(matcher.group(1));
            }
        }

        return "";
    }

    private String buildTitle(String description, String category, String locationText) {
        String issue = inferIssueName(description, category);
        String location = cleanText(locationText);
        String title = location.isBlank() ? issue + "报修" : location + issue;
        return title.length() > 60 ? title.substring(0, 60) : title;
    }

    private String inferIssueName(String description, String category) {
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (text.contains("漏水") || text.contains("滴水") || text.contains("积水")) {
            return "漏水故障";
        }
        if (text.contains("堵塞") || text.contains("下水") || text.contains("地漏")) {
            return "堵塞故障";
        }
        if (text.contains("频闪") || text.contains("照明") || text.contains("灯")) {
            return "照明故障";
        }
        if (text.contains("插座")) {
            return "插座故障";
        }
        if (text.contains("断电") || text.contains("跳闸")) {
            return "电力故障";
        }
        if (text.contains("wifi") || text.contains("网络") || text.contains("网口")) {
            return "网络故障";
        }
        if (text.contains("空调")) {
            return "空调故障";
        }
        if (text.contains("门锁") || text.contains("门")) {
            return "门锁故障";
        }
        if (text.contains("窗") || text.contains("玻璃")) {
            return "门窗故障";
        }
        if (text.contains("桌") || text.contains("椅") || text.contains("床") || text.contains("柜")) {
            return "家具故障";
        }
        if (category != null && category.endsWith("故障")) {
            return category;
        }
        return "维修问题";
    }

    private String cleanLocation(String value) {
        String cleaned = cleanText(value);
        cleaned = cleaned.replaceAll("^[在于到至\\s]+", "");
        cleaned = cleaned.replaceAll("[的,，。；;：:\\s]+$", "");
        return cleaned;
    }

    private String cleanText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String shortText(String value, int maxLength) {
        String text = cleanText(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = cleanText(value);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return "";
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
