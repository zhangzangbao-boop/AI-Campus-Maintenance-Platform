package com.qiyun.repairservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.common.exception.BusinessException;
import com.qiyun.feign.client.AiServiceClient;
import com.qiyun.feign.dto.AnalyzeTicketRequest;
import com.qiyun.feign.dto.AnalyzeTicketResponse;
import com.qiyun.repairservice.domain.entity.AiTicketAnalysis;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.dto.CompletionSummaryDto;
import com.qiyun.repairservice.repository.AiTicketAnalysisRepository;
import com.qiyun.repairservice.repository.TicketRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketCompletionSummaryService {

    private final TicketRepository ticketRepository;
    private final AiTicketAnalysisRepository aiTicketAnalysisRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void generateAsync(Long ticketId) {
        try {
            generateAndSave(ticketId, false);
        } catch (Exception e) {
            log.warn("Completion summary generation skipped: ticketId={}, error={}", ticketId, e.getMessage());
        }
    }

    @Transactional
    public CompletionSummaryDto regenerate(Long ticketId) {
        return generateAndSave(ticketId, true);
    }

    @Transactional(readOnly = true)
    public CompletionSummaryDto getByTicketId(Long ticketId) {
        return aiTicketAnalysisRepository.findByTicketId(ticketId)
            .filter(analysis -> hasText(analysis.getSummary()))
            .map(this::toDto)
            .orElse(null);
    }

    private CompletionSummaryDto generateAndSave(Long ticketId, boolean force) {
        RepairTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工单不存在"));

        if (!isCompletedStatus(ticket.getStatus())) {
            throw new BusinessException("仅已完成或已关闭的工单可生成完成总结");
        }

        AiTicketAnalysis analysis = aiTicketAnalysisRepository.findByTicketId(ticketId).orElseGet(AiTicketAnalysis::new);
        if (!force && hasText(analysis.getSummary())) {
            return toDto(analysis);
        }

        AnalyzeTicketResponse aiResponse = tryAnalyze(ticket);
        boolean aiUsed = aiResponse != null;
        String source = aiUsed ? "AI" : "RULE";
        LocalDateTime now = LocalDateTime.now();
        SummaryParts parts = buildSummaryParts(ticket, aiResponse);
        String summary = formatSummary(parts);

        analysis.setTicketId(ticket.getTicketId());
        analysis.setTitle("工单完成总结");
        analysis.setSourceText(buildSourceText(ticket));
        analysis.setCategoryKey(ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : null);
        analysis.setLocationText(ticket.getLocationText());
        analysis.setPriority(ticket.getPriority());
        analysis.setUrgency(aiResponse != null ? aiResponse.urgency() : null);
        analysis.setSuggestion(parts.followUpAdvice());
        analysis.setKeywords(aiResponse != null ? toJson(aiResponse.keywords()) : "[]");
        analysis.setSummary(summary);
        analysis.setProvider(aiUsed ? "qiyun-ai-service" : "rule-fallback");
        analysis.setModel(aiUsed ? "analyze-ticket + completion-summary-v1" : "completion-summary-v1");
        analysis.setRawResponse(toJson(Map.of(
            "source", source,
            "failureCause", parts.failureCause(),
            "repairProcess", parts.repairProcess(),
            "materialsUsed", parts.materialsUsed(),
            "result", parts.result(),
            "duration", parts.duration(),
            "followUpAdvice", parts.followUpAdvice(),
            "generatedAt", now.toString()
        )));
        analysis.setCreatedAt(now);

        return toDto(aiTicketAnalysisRepository.save(analysis));
    }

    private AnalyzeTicketResponse tryAnalyze(RepairTicket ticket) {
        try {
            Map<String, Object> response = aiServiceClient.analyzeTicket(
                new AnalyzeTicketRequest(buildSourceText(ticket), ticket.getLocationText())
            );
            return aiServiceClient.extractResponse(response);
        } catch (Exception e) {
            log.warn("AI completion summary hint unavailable: ticketId={}, error={}", ticket.getTicketId(), e.getMessage());
            return null;
        }
    }

    private SummaryParts buildSummaryParts(RepairTicket ticket, AnalyzeTicketResponse aiResponse) {
        String category = ticket.getCategory() != null ? ticket.getCategory().getCategoryName() : "未分类故障";
        String repairNotes = firstText(ticket.getRepairNotes(), ticket.getProcessNotes(), "维修人员已完成现场处理，未填写详细维修备注");
        String processNotes = firstText(ticket.getProcessNotes(), ticket.getRepairNotes(), "按报修问题进行现场排查和处理");
        String materials = inferMaterials(ticket);
        String result = switch (ticket.getStatus()) {
            case RESOLVED -> "维修已完成，等待后续确认或评价。";
            case CLOSED, FEEDBACKED -> "工单已关闭，维修处理流程已完成。";
            case WAITING_FEEDBACK -> "维修已完成，等待用户反馈。";
            default -> "维修处理已记录。";
        };
        String aiSuggestion = aiResponse != null && hasText(aiResponse.suggestion()) ? aiResponse.suggestion() : null;
        String followUp = firstText(aiSuggestion, fallbackAdvice(ticket));
        String cause = inferCause(ticket, category, aiResponse);

        return new SummaryParts(
            cause,
            processNotes,
            materials,
            result,
            formatDuration(ticket),
            followUp
        );
    }

    private String inferCause(RepairTicket ticket, String category, AnalyzeTicketResponse aiResponse) {
        if (aiResponse != null && hasText(aiResponse.category())) {
            return "结合工单描述和 AI 分析，故障归类为 " + aiResponse.category() + "，现场原因以维修记录为准。";
        }
        String description = firstText(ticket.getDescription(), "未填写故障描述");
        return "根据报修描述“" + description + "”，故障初步归类为 " + category + "，具体原因以现场排查记录为准。";
    }

    private String inferMaterials(RepairTicket ticket) {
        String notes = (firstText(ticket.getRepairNotes(), "") + " " + firstText(ticket.getProcessNotes(), "")).toLowerCase();
        if (notes.contains("filter") || notes.contains("滤网")) {
            return "清洁工具、滤网相关耗材（如有更换以现场记录为准）";
        }
        if (notes.contains("制冷剂") || notes.contains("氟")) {
            return "制冷剂及空调检修工具";
        }
        if (notes.contains("水管") || notes.contains("阀") || notes.contains("漏水")) {
            return "管件、密封材料及基础维修工具";
        }
        if (notes.contains("灯") || notes.contains("电") || notes.contains("线路")) {
            return "电工工具及必要电气耗材";
        }
        return "未记录具体更换材料，按现场实际耗材为准";
    }

    private String fallbackAdvice(RepairTicket ticket) {
        String description = firstText(ticket.getDescription(), "").toLowerCase();
        if (description.contains("空调") || description.contains("制冷")) {
            return "建议定期清洁滤网，后续如再次出现制冷异常，及时记录故障现象并报修。";
        }
        if (description.contains("漏水") || description.contains("水管")) {
            return "建议继续观察接口和阀门处是否渗漏，发现复发及时关闭水源并报修。";
        }
        return "建议后续观察使用情况，如同类问题复发，补充现场照片和现象说明后重新报修。";
    }

    private String formatDuration(RepairTicket ticket) {
        LocalDateTime start = ticket.getAssignedAt() != null ? ticket.getAssignedAt() : ticket.getCreatedAt();
        LocalDateTime end = firstTime(ticket.getCompletedAt(), ticket.getClosedAt(), LocalDateTime.now());
        if (start == null || end == null || end.isBefore(start)) {
            return "暂无可计算耗时";
        }
        Duration duration = Duration.between(start, end);
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        if (hours <= 0) {
            return minutes + "分钟";
        }
        return hours + "小时" + minutes + "分钟";
    }

    private String formatSummary(SummaryParts parts) {
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("故障原因", parts.failureCause());
        lines.put("维修过程", parts.repairProcess());
        lines.put("使用材料", parts.materialsUsed());
        lines.put("处理结果", parts.result());
        lines.put("耗时", parts.duration());
        lines.put("后续建议", parts.followUpAdvice());
        return lines.entrySet().stream()
            .map(entry -> entry.getKey() + "：" + entry.getValue())
            .reduce((left, right) -> left + System.lineSeparator() + right)
            .orElse("");
    }

    private String buildSourceText(RepairTicket ticket) {
        return "位置：" + firstText(ticket.getLocationText(), "未填写")
            + "\n故障描述：" + firstText(ticket.getDescription(), "未填写")
            + "\n维修备注：" + firstText(ticket.getRepairNotes(), "未填写")
            + "\n过程记录：" + firstText(ticket.getProcessNotes(), "未填写");
    }

    private CompletionSummaryDto toDto(AiTicketAnalysis analysis) {
        String source = "rule-fallback".equals(analysis.getProvider()) ? "RULE" : "AI";
        return new CompletionSummaryDto(
            analysis.getTicketId(),
            analysis.getSummary(),
            source,
            analysis.getProvider(),
            analysis.getModel(),
            analysis.getCreatedAt()
        );
    }

    private boolean isCompletedStatus(TicketStatus status) {
        return status == TicketStatus.RESOLVED
            || status == TicketStatus.WAITING_FEEDBACK
            || status == TicketStatus.CLOSED
            || status == TicketStatus.FEEDBACKED;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private LocalDateTime firstTime(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record SummaryParts(
        String failureCause,
        String repairProcess,
        String materialsUsed,
        String result,
        String duration,
        String followUpAdvice
    ) {
    }
}
