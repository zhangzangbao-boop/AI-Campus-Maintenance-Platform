package com.ligong.reportingcenter.service;

import com.ligong.reportingcenter.domain.entity.AiTicketAnalysis;
import com.ligong.reportingcenter.domain.entity.Category;
import com.ligong.reportingcenter.domain.entity.Rating;
import com.ligong.reportingcenter.domain.entity.RepairTicket;
import com.ligong.reportingcenter.domain.enums.TicketStatus;
import com.ligong.reportingcenter.dto.AiTicketAnalysisDto;
import com.ligong.reportingcenter.dto.KnowledgeBaseDto;
import com.ligong.reportingcenter.dto.SimilarTicketDto;
import com.ligong.reportingcenter.dto.request.AiKnowledgeDraftRequest;
import com.ligong.reportingcenter.dto.request.AiTicketAnalyzeRequest;
import com.ligong.reportingcenter.dto.request.KnowledgeBaseRequest;
import com.ligong.reportingcenter.dto.request.SimilarTicketRequest;
import com.qiyun.common.exception.BusinessException;
import com.ligong.reportingcenter.repository.AiTicketAnalysisRepository;
import com.ligong.reportingcenter.repository.RatingRepository;
import com.ligong.reportingcenter.repository.TicketRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private static final Pattern LOCATION_PATTERN = Pattern.compile(
        "((?:[一二三四五六七八九十0-9]+号)?(?:宿舍楼|教学楼|实验楼|图书馆|食堂|体育馆|综合楼|信息楼|校医院|操场|篮球场|南门|北门|东门|西门)[^，。,.；;\\n]{0,24})");

    private final AiTicketAnalysisRepository aiTicketAnalysisRepository;
    private final TicketRepository ticketRepository;
    private final RatingRepository ratingRepository;
    private final CategoryService categoryService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DeepSeekClientService deepSeekClientService;

    @Transactional
    public AiTicketAnalysisDto analyzeTicket(AiTicketAnalyzeRequest request) {
        String sourceText = normalizeText(request.text());
        if (sourceText.isBlank()) {
            throw new BusinessException("请先输入需要智能识别的报修描述");
        }

        List<Category> categories = categoryService.listAllCategories();
        RuleAnalysis analysis = analyzeByRules(sourceText, request.locationText(), request.categoryKey(), categories);
        String rawResponse = "规则引擎生成；外部 AI 未启用、未配置密钥或调用失败。";

        if (deepSeekClientService.available()) {
            AiModelResult modelResult = analyzeByModel(sourceText, request.locationText(), request.categoryKey(), categories);
            if (modelResult.analysis() != null) {
                analysis = modelResult.analysis();
                rawResponse = modelResult.rawResponse();
            }
        }

        List<SimilarTicketDto> similarTickets = findSimilarTickets(new SimilarTicketRequest(
            analysis.summary(),
            analysis.locationText(),
            analysis.categoryKey(),
            5
        ));
        List<KnowledgeBaseDto> knowledge = knowledgeBaseService.recommend(
            analysis.categoryKey(),
            sourceText,
            3
        );

        AiTicketAnalysis entity = new AiTicketAnalysis();
        entity.setSourceText(sourceText);
        entity.setTitle(analysis.title());
        entity.setCategoryKey(analysis.categoryKey());
        entity.setLocationText(analysis.locationText());
        entity.setPriority(analysis.priority());
        entity.setSummary(analysis.summary());
        entity.setSafetyTips(analysis.safetyTips());
        entity.setProvider(effectiveSource());
        entity.setModel(deepSeekClientService.modelName());
        entity.setRawResponse(rawResponse);
        aiTicketAnalysisRepository.save(entity);

        return new AiTicketAnalysisDto(
            analysis.title(),
            analysis.categoryKey(),
            analysis.categoryKey(),
            analysis.locationText(),
            analysis.priority(),
            analysis.summary(),
            analysis.safetyTips(),
            effectiveSource(),
            similarTickets,
            knowledge
        );
    }

    @Transactional(readOnly = true)
    public List<SimilarTicketDto> findSimilarTickets(SimilarTicketRequest request) {
        String text = normalizeText(request.description());
        String location = normalizeText(request.locationText());
        String categoryKey = normalizeText(request.categoryKey());
        int limit = request.limit() == null || request.limit() <= 0 ? 5 : Math.min(request.limit(), 10);

        Set<String> queryTokens = tokenize(text + " " + location + " " + categoryKey);
        if (queryTokens.isEmpty() && categoryKey.isBlank() && location.isBlank()) {
            return List.of();
        }

        return ticketRepository.findAll().stream()
            .filter(ticket -> !Boolean.TRUE.equals(ticket.getDeleted()))
            .map(ticket -> scoreTicket(ticket, queryTokens, categoryKey, location))
            .filter(item -> item.score() > 0)
            .sorted(Comparator.comparingDouble(ScoredTicket::score).reversed()
                .thenComparing(item -> item.ticket().getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(limit)
            .map(item -> toSimilarTicketDto(item.ticket(), item.score()))
            .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAiStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("aiEnabled", deepSeekClientService.enabled());
        status.put("aiConfigured", deepSeekClientService.configured());
        status.put("aiProvider", deepSeekClientService.providerName());
        status.put("aiModel", deepSeekClientService.modelName());
        status.put("deepSeekAvailable", deepSeekClientService.available());
        status.put("capabilities", List.of(
            "学生报修智能填写",
            "相似历史工单规则检索",
            "管理员工单摘要",
            "维修员维修报告生成",
            "管理员知识库条目生成"
        ));
        status.put("fallback", "未配置 DeepSeek 时使用本地规则引擎和关键词相似度，可满足离线演示。");
        return status;
    }

    public String generateRepairReport(String description, String processNotes) {
        String problem = normalizeText(description);
        String process = normalizeText(processNotes);
        if (deepSeekClientService.available()) {
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

    @Transactional(readOnly = true)
    public Map<String, Object> summarizeTicket(Long ticketId) {
        RepairTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException("报修单不存在"));
        String baseText = String.join("\n",
            "分类：" + (ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : "未分类"),
            "地点：" + safeForPrompt(ticket.getLocationText()),
            "状态：" + (ticket.getStatus() != null ? ticket.getStatus().name() : "未知"),
            "优先级：" + safeForPrompt(ticket.getPriority()),
            "学生描述：" + safeForPrompt(ticket.getDescription()),
            "维修备注：" + safeForPrompt(ticket.getRepairNotes()),
            "处理过程：" + safeForPrompt(ticket.getProcessNotes()),
            "驳回原因：" + safeForPrompt(ticket.getRejectionReason())
        );
        String summary = "";
        if (deepSeekClientService.available()) {
            summary = deepSeekClientService.requestText(
                "你是校园维修调度助手。请根据工单信息生成中文摘要，包括问题要点、风险等级、建议下一步，每项不超过一句。",
                baseText,
                500
            );
        }
        if (summary.isBlank()) {
            summary = buildFallbackTicketSummary(ticket);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ticketId", ticketId);
        result.put("summary", summary);
        result.put("source", deepSeekClientService.available() && !summary.equals(buildFallbackTicketSummary(ticket)) ? effectiveSource() : "本地规则引擎");
        result.put("generatedAt", java.time.LocalDateTime.now());
        return result;
    }

    public KnowledgeBaseRequest draftKnowledge(AiKnowledgeDraftRequest request) {
        String categoryKey = request == null ? "" : normalizeText(request.categoryKey());
        String symptomText = request == null ? "" : normalizeText(request.symptomText());
        if (categoryKey.isBlank()) {
            throw new BusinessException("请选择知识库分类");
        }
        if (symptomText.isBlank()) {
            throw new BusinessException("请输入故障现象或处理目标");
        }

        KnowledgeBaseRequest fallbackDraft = buildFallbackKnowledgeDraft(categoryKey, symptomText);
        if (!deepSeekClientService.available()) {
            return fallbackDraft;
        }

        Map<String, Object> json = deepSeekClientService.requestJson(
            "你是校园后勤维修知识库编辑助手。请根据故障现象生成可落库的中文维修知识条目，只输出 JSON 对象。"
                + "字段必须包含 title, symptomKeywords, solutionSteps, safetyNotes, estimatedMinutes。"
                + "solutionSteps 使用 4 到 6 条编号步骤，安全注意事项要具体、可执行，不要编造昂贵设备型号。",
            "分类：" + categoryKey
                + "\n故障现象或处理目标：" + symptomText
                + "\n请生成适合校园维修人员使用的标准知识库条目。",
            900
        );
        if (json.isEmpty()) {
            return fallbackDraft;
        }

        return new KnowledgeBaseRequest(
            categoryKey,
            fallback(stringValue(json.get("title")), fallbackDraft.title()),
            fallback(stringValue(json.get("symptomKeywords")), fallbackDraft.symptomKeywords()),
            fallback(stringValue(json.get("solutionSteps")), fallbackDraft.solutionSteps()),
            fallback(stringValue(json.get("safetyNotes")), fallbackDraft.safetyNotes()),
            positiveInt(json.get("estimatedMinutes"), fallbackDraft.estimatedMinutes()),
            true
        );
    }

    private AiModelResult analyzeByModel(String text, String locationText, String categoryKey, List<Category> categories) {
        String categoriesText = categories.stream()
            .map(Category::getCategoryName)
            .toList()
            .toString();
        Map<String, Object> json = deepSeekClientService.requestJson(
            "你是校园报修系统的智能填单助手。必须只输出 JSON 对象，不要输出 Markdown。"
                + "字段：title, categoryKey, locationText, priority, summary, safetyTips。"
                + "categoryKey 必须从给定分类中选择；priority 只能是 low、medium、high。",
            "可选分类：" + categoriesText
                + "\n用户描述：" + text
                + "\n用户已填位置：" + safeForPrompt(locationText)
                + "\n用户已选分类：" + safeForPrompt(categoryKey),
            700
        );
        if (json.isEmpty()) {
            return new AiModelResult(null, "");
        }
        String category = stringValue(json.get("categoryKey"));
        if (firstExistingCategory(categories, category) == null) {
            category = firstExistingCategory(categories, categoryKey);
        }
        if (category == null) {
            category = analyzeByRules(text, locationText, categoryKey, categories).categoryKey();
        }
        String priority = stringValue(json.get("priority")).toLowerCase(Locale.ROOT);
        if (!List.of("low", "medium", "high").contains(priority)) {
            priority = inferPriority(text.toLowerCase(Locale.ROOT));
        }
        RuleAnalysis analysis = new RuleAnalysis(
            fallback(stringValue(json.get("title")), buildTitle(category, text, stringValue(json.get("locationText")))),
            category,
            fallback(stringValue(json.get("locationText")), extractLocation(text)),
            priority,
            fallback(stringValue(json.get("summary")), buildSummary(category, text, stringValue(json.get("locationText")))),
            fallback(stringValue(json.get("safetyTips")), buildSafetyTips(category, text.toLowerCase(Locale.ROOT)))
        );
        if (analysis.locationText().isBlank()) {
            analysis = new RuleAnalysis(
                analysis.title(),
                analysis.categoryKey(),
                "请补充具体楼栋、楼层和房间号",
                analysis.priority(),
                analysis.summary(),
                analysis.safetyTips()
            );
        }
        return new AiModelResult(analysis, json.toString());
    }

    private RuleAnalysis analyzeByRules(String text, String locationText, String categoryKey, List<Category> categories) {
        String lower = text.toLowerCase(Locale.ROOT);
        String category = firstExistingCategory(categories, categoryKey);

        if (category == null) {
            if (containsAny(lower, "漏水", "滴水", "水管", "水龙头", "下水", "地漏", "积水", "跳闸", "总闸")) {
                category = firstExistingCategory(categories, "水电维修", "公共设施");
            } else if (containsAny(lower, "网络", "网口", "wifi", "无线", "断线", "校园网", "交换机", "无法连接")) {
                category = firstExistingCategory(categories, "网络故障");
            } else if (containsAny(lower, "桌", "椅", "床", "柜", "门锁", "桌腿", "靠背")) {
                category = firstExistingCategory(categories, "家具维修", "门窗维修");
            } else if (containsAny(lower, "空调", "灯", "照明", "插座", "插排", "电器", "多媒体", "投影")) {
                category = firstExistingCategory(categories, "电器故障", "空调维修");
            } else if (containsAny(lower, "消防", "灭火器", "应急灯", "烟感")) {
                category = firstExistingCategory(categories, "消防安全", "公共设施");
            } else if (containsAny(lower, "门", "窗", "玻璃", "闭门器")) {
                category = firstExistingCategory(categories, "门窗维修", "公共设施");
            } else if (containsAny(lower, "卫生", "清洁", "异味", "垃圾", "堵塞")) {
                category = firstExistingCategory(categories, "卫生清洁", "公共设施");
            }
        }
        if (category == null) {
            category = categories.isEmpty() ? "其他" : categories.get(0).getCategoryName();
        }

        String location = normalizeText(locationText);
        if (location.isBlank()) {
            location = extractLocation(text);
        }
        if (location.isBlank()) {
            location = "请补充具体楼栋、楼层和房间号";
        }

        String priority = inferPriority(lower);
        String title = buildTitle(category, text, location);
        String summary = buildSummary(category, text, location);
        String safetyTips = buildSafetyTips(category, lower);

        return new RuleAnalysis(title, category, location, priority, summary, safetyTips);
    }

    private String firstExistingCategory(List<Category> categories, String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            for (Category category : categories) {
                if (candidate.equals(category.getCategoryName())) {
                    return category.getCategoryName();
                }
            }
        }
        return null;
    }

    private String inferPriority(String lower) {
        if (containsAny(lower, "漏水", "积水", "触电", "烧焦", "冒烟", "火花", "异味", "总闸", "消防", "灭火器", "玻璃破裂", "危险")) {
            return "high";
        }
        if (containsAny(lower, "无法使用", "不能用", "断线", "频闪", "损坏", "不制冷", "堵塞", "松动")) {
            return "medium";
        }
        return "low";
    }

    private String extractLocation(String text) {
        Matcher matcher = LOCATION_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private String buildTitle(String category, String text, String location) {
        String issue;
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "漏水", "滴水", "水管")) {
            issue = "水管漏水";
        } else if (containsAny(lower, "网络", "断线", "无法连接")) {
            issue = "网络连接异常";
        } else if (containsAny(lower, "灯", "照明", "频闪")) {
            issue = "照明设备异常";
        } else if (containsAny(lower, "空调")) {
            issue = "空调运行异常";
        } else if (containsAny(lower, "插座", "插排")) {
            issue = "插座用电异常";
        } else if (containsAny(lower, "桌", "椅", "床", "柜")) {
            issue = "家具损坏";
        } else if (containsAny(lower, "门", "窗", "玻璃")) {
            issue = "门窗设施损坏";
        } else {
            issue = category + "问题";
        }
        String area = location == null || location.isBlank() || location.startsWith("请补充")
            ? ""
            : location.replaceAll("[，。,.；;].*$", "");
        return shortText((area.isBlank() ? "" : area + " ") + issue, 80);
    }

    private String buildSummary(String category, String text, String location) {
        return "系统识别为“" + category + "”，地点为“" + location + "”。原始描述："
            + shortText(text, 160);
    }

    private String buildFallbackTicketSummary(RepairTicket ticket) {
        String category = ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : "未分类";
        String location = safeForPrompt(ticket.getLocationText());
        String priority = safeForPrompt(ticket.getPriority());
        String status = ticket.getStatus() != null ? ticket.getStatus().name() : "未知状态";
        String risk = "high".equalsIgnoreCase(priority) ? "高风险" : "medium".equalsIgnoreCase(priority) ? "中风险" : "低风险";
        String next = switch (ticket.getStatus()) {
            case WAITING_ACCEPT -> "建议管理员尽快派单。";
            case IN_PROGRESS -> "建议维修人员补充过程记录并按 SLA 完成。";
            case RESOLVED, WAITING_FEEDBACK -> "建议提醒学生确认并评价。";
            case REJECTED, CLOSED, FEEDBACKED -> "建议归档并用于后续统计分析。";
        };
        return "问题要点：" + category + "，地点：" + location + "，当前状态：" + status + "。"
            + "风险等级：" + risk + "。"
            + "下一步：" + next;
    }

    private String buildSafetyTips(String category, String lower) {
        if (containsAny(lower, "烧焦", "冒烟", "火花", "触电", "插座", "插排", "总闸")) {
            return "疑似用电安全问题，请立即停止使用相关设备，不要自行拆卸插座或线路，并远离异常区域。";
        }
        if (containsAny(lower, "漏水", "积水", "地漏", "水管")) {
            return "请暂时避开积水区域，必要时放置防滑提示，避免滑倒或水电混合风险。";
        }
        if (containsAny(lower, "玻璃", "断裂", "松动", "脱落")) {
            return "请避免继续使用或触碰损坏设施，必要时用明显物品隔离现场，防止划伤或砸伤。";
        }
        if ("消防安全".equals(category)) {
            return "消防设施异常请保持通道畅通，不要擅自挪用设备，等待专业人员检查。";
        }
        return "请保留现场照片并补充准确位置，等待管理员分配维修人员处理。";
    }

    private KnowledgeBaseRequest buildFallbackKnowledgeDraft(String categoryKey, String symptomText) {
        String lower = symptomText.toLowerCase(Locale.ROOT);
        String title = categoryKey + "处理建议：" + shortText(symptomText, 28);
        Set<String> tokens = tokenize(categoryKey + " " + symptomText);
        String keywords = tokens.isEmpty() ? categoryKey : String.join("，", tokens);
        String steps;
        int estimatedMinutes;
        if ("水电维修".equals(categoryKey) || containsAny(lower, "漏水", "水管", "插座", "跳闸")) {
            steps = "1. 到场后先确认故障位置和影响范围；2. 涉及漏水或用电风险时先关闭对应阀门或电源；3. 检查连接件、阀芯、线路或插座状态；4. 更换损坏配件并完成通水/通电测试；5. 清理现场并提醒学生后续观察。";
            estimatedMinutes = 45;
        } else if ("网络故障".equals(categoryKey) || containsAny(lower, "网络", "网口", "无线", "校园网")) {
            steps = "1. 确认报修设备、网口和网络账号状态；2. 使用测试设备检查端口连通性；3. 重新压接水晶头或更换短网线；4. 必要时重启 AP/交换机端口；5. 现场验证校园网访问恢复。";
            estimatedMinutes = 40;
        } else if ("家具维修".equals(categoryKey) || containsAny(lower, "桌", "椅", "床", "柜")) {
            steps = "1. 判断家具损坏位置和结构稳定性；2. 可加固时补紧螺丝或更换连接件；3. 结构断裂时登记更换；4. 回收损坏部件并清理尖锐边角；5. 完成试用确认。";
            estimatedMinutes = 35;
        } else if ("空调维修".equals(categoryKey) || containsAny(lower, "空调", "制冷", "异响")) {
            steps = "1. 记录空调运行状态和异常声音；2. 清洗滤网并检查出风口；3. 检查室外机散热和排水；4. 测试制冷效果；5. 如涉及压缩机或制冷剂，登记后联系专业维保。";
            estimatedMinutes = 90;
        } else {
            steps = "1. 到场核实故障现象；2. 判断是否影响安全和正常使用；3. 根据分类选择维修工具和备件；4. 完成处理后进行功能复测；5. 在工单中记录处理过程和后续建议。";
            estimatedMinutes = 50;
        }
        return new KnowledgeBaseRequest(
            categoryKey,
            title,
            keywords,
            steps,
            buildSafetyTips(categoryKey, lower),
            estimatedMinutes,
            true
        );
    }

    private ScoredTicket scoreTicket(RepairTicket ticket, Set<String> queryTokens, String categoryKey, String location) {
        double score = 0.0;
        String ticketCategory = ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : "";
        if (!categoryKey.isBlank() && categoryKey.equals(ticketCategory)) {
            score += 0.25;
        }

        String ticketLocation = normalizeText(ticket.getLocationText());
        if (!location.isBlank()) {
            if (ticketLocation.contains(location) || location.contains(ticketLocation)) {
                score += 0.2;
            } else if (!firstLocationToken(location).isBlank()
                && ticketLocation.contains(firstLocationToken(location))) {
                score += 0.12;
            }
        }

        Set<String> ticketTokens = tokenize(ticket.getDescription() + " " + ticketLocation + " " + ticketCategory);
        if (!queryTokens.isEmpty() && !ticketTokens.isEmpty()) {
            Set<String> intersection = new HashSet<>(queryTokens);
            intersection.retainAll(ticketTokens);
            Set<String> union = new HashSet<>(queryTokens);
            union.addAll(ticketTokens);
            score += union.isEmpty() ? 0 : intersection.size() * 1.0 / union.size() * 0.55;
        }

        if (ticket.getStatus() == TicketStatus.WAITING_ACCEPT || ticket.getStatus() == TicketStatus.IN_PROGRESS) {
            score += 0.08;
        }
        return new ScoredTicket(ticket, Math.min(0.98, Math.round(score * 100.0) / 100.0));
    }

    private SimilarTicketDto toSimilarTicketDto(RepairTicket ticket, double similarity) {
        Integer ratingScore = ratingRepository.findByTicket(ticket)
            .map(Rating::getScore)
            .orElse(null);
        return new SimilarTicketDto(
            ticket.getTicketId(),
            ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : null,
            ticket.getLocationText(),
            ticket.getDescription(),
            ticket.getStatus(),
            ticket.getStaff() != null ? ticket.getStaff().getUserId() : null,
            ticket.getRepairNotes(),
            ticket.getProcessNotes(),
            ratingScore,
            similarity,
            ticket.getCreatedAt()
        );
    }

    private Set<String> tokenize(String text) {
        String normalized = normalizeText(text).toLowerCase(Locale.ROOT)
            .replaceAll("[，。！？、；：,.!?;:()（）\\[\\]【】]", " ");
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : normalized.split("\\s+")) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        List<String> keywords = List.of(
            "漏水", "滴水", "积水", "水管", "水龙头", "地漏", "跳闸", "插座", "烧焦", "照明", "频闪",
            "网络", "断线", "无线", "网口", "交换机", "空调", "制冷", "异响", "桌", "椅", "门锁",
            "玻璃", "闭门器", "消防", "灭火器", "应急灯", "堵塞", "异味"
        );
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                tokens.add(keyword);
            }
        }
        return tokens;
    }

    private String firstLocationToken(String location) {
        String normalized = normalizeText(location);
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.split("[\\s-]+")[0];
    }

    private boolean containsAny(String text, String... tokens) {
        if (text == null) {
            return false;
        }
        for (String token : tokens) {
            if (text.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String shortText(String text, int maxLength) {
        String normalized = normalizeText(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? normalizeText(fallback) : value;
    }

    private Integer positiveInt(Object value, Integer fallback) {
        try {
            int parsed = value instanceof Number number
                ? number.intValue()
                : Integer.parseInt(stringValue(value));
            return parsed > 0 ? Math.min(parsed, 10080) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String safeForPrompt(String value) {
        return value == null || value.isBlank() ? "未填写" : value.trim();
    }

    private String effectiveSource() {
        if (deepSeekClientService.available()) {
            return deepSeekClientService.providerName() + "（已配置，当前保留规则兜底）";
        }
        return "本地规则引擎";
    }

    private record RuleAnalysis(
        String title,
        String categoryKey,
        String locationText,
        String priority,
        String summary,
        String safetyTips
    ) {
    }

    private record ScoredTicket(RepairTicket ticket, double score) {
    }

    private record AiModelResult(RuleAnalysis analysis, String rawResponse) {
    }
}
