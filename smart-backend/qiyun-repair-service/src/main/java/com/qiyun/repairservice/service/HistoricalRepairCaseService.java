package com.qiyun.repairservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.feign.client.AiInternalClient;
import com.qiyun.feign.client.AiInternalClient.RepairCaseSearchRequest;
import com.qiyun.feign.client.AiInternalClient.RepairCaseSyncRequest;
import com.qiyun.repairservice.domain.entity.AiTicketAnalysis;
import com.qiyun.repairservice.domain.entity.RepairProcessRecord;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.dto.HistoricalRepairCaseDto;
import com.qiyun.repairservice.repository.AiTicketAnalysisRepository;
import com.qiyun.repairservice.repository.RepairProcessRecordRepository;
import com.qiyun.repairservice.repository.TicketRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricalRepairCaseService {

    private static final double MIN_SIMILARITY = 0.35;
    private static final int DEFAULT_LIMIT = 5;

    private final TicketRepository ticketRepository;
    private final RepairProcessRecordRepository processRecordRepository;
    private final AiTicketAnalysisRepository aiTicketAnalysisRepository;
    private final AiInternalClient aiInternalClient;
    private final ObjectMapper objectMapper;

    @Value("${internal.service.secret:}")
    private String internalSecret;

    @Async
    @Transactional(readOnly = true)
    public void syncTicketAsync(Long ticketId) {
        try {
            RepairTicket ticket = ticketRepository.findById(ticketId).orElse(null);
            if (ticket == null || ticket.getStudentConfirmedAt() == null || Boolean.TRUE.equals(ticket.getDeleted())) {
                return;
            }
            RepairCaseParts parts = buildParts(ticket);
            aiInternalClient.syncRepairCase(getInternalSecret(), ticketId, new RepairCaseSyncRequest(
                buildDocument(ticket, parts),
                categoryName(ticket),
                categoryName(ticket),
                parts.failureCause(),
                parts.repairMethod(),
                parts.materials(),
                parts.result()
            ));
            log.info("Historical repair case synced: ticketId={}", ticketId);
        } catch (Exception e) {
            log.warn("Historical repair case sync degraded: ticketId={}, error={}", ticketId, e.getMessage());
        }
    }

    @Async
    public void deleteTicketAsync(Long ticketId) {
        try {
            aiInternalClient.deleteRepairCase(getInternalSecret(), ticketId);
        } catch (Exception e) {
            log.warn("Historical repair case delete skipped: ticketId={}, error={}", ticketId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<HistoricalRepairCaseDto> recommendForTicket(Long ticketId, int limit) {
        RepairTicket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) {
            return List.of();
        }
        int size = normalizedLimit(limit);
        String query = buildQuery(ticket);
        try {
            Map<String, Object> response = aiInternalClient.searchRepairCases(
                getInternalSecret(),
                new RepairCaseSearchRequest(query, categoryName(ticket), size)
            );
            List<HistoricalRepairCaseDto> vectorResults = parseVectorResults(response, ticketId, size);
            if (!vectorResults.isEmpty()) {
                return vectorResults;
            }
        } catch (Exception e) {
            log.warn("Historical repair vector search degraded: ticketId={}, error={}", ticketId, e.getMessage());
        }
        return fallbackByCategory(ticket, size);
    }

    @Transactional(readOnly = true)
    public int rebuildIndex() {
        List<RepairTicket> tickets = ticketRepository.findConfirmedCompletedTickets();
        int count = 0;
        for (RepairTicket ticket : tickets) {
            try {
                RepairCaseParts parts = buildParts(ticket);
                aiInternalClient.syncRepairCase(getInternalSecret(), ticket.getTicketId(), new RepairCaseSyncRequest(
                    buildDocument(ticket, parts),
                    categoryName(ticket),
                    categoryName(ticket),
                    parts.failureCause(),
                    parts.repairMethod(),
                    parts.materials(),
                    parts.result()
                ));
                count++;
            } catch (Exception e) {
                log.warn("Historical repair case rebuild skipped: ticketId={}, error={}", ticket.getTicketId(), e.getMessage());
            }
        }
        return count;
    }

    private List<HistoricalRepairCaseDto> parseVectorResults(Map<String, Object> response, Long currentTicketId, int limit) {
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof List<?> rows)) {
            return List.of();
        }
        List<HistoricalRepairCaseDto> results = new ArrayList<>();
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map<?, ?> row)) {
                continue;
            }
            Double similarity = asDouble(row.get("similarity"));
            if (similarity == null || similarity < MIN_SIMILARITY) {
                continue;
            }
            Map<String, Object> metadata = metadata(row.get("metadata"));
            Long ticketId = asLong(metadata.get("ticketId"));
            if (ticketId != null && ticketId.equals(currentTicketId)) {
                continue;
            }
            results.add(new HistoricalRepairCaseDto(
                ticketId,
                text(metadata.get("categoryName")),
                similarity,
                text(metadata.get("failureCause")),
                text(metadata.get("repairMethod")),
                text(metadata.get("materials")),
                text(metadata.get("result")),
                false
            ));
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    private List<HistoricalRepairCaseDto> fallbackByCategory(RepairTicket ticket, int limit) {
        return ticketRepository.findConfirmedCompletedTicketsForFallback(categoryName(ticket), ticket.getTicketId()).stream()
            .limit(limit)
            .map(item -> {
                RepairCaseParts parts = buildParts(item);
                return new HistoricalRepairCaseDto(
                    item.getTicketId(),
                    categoryName(item),
                    null,
                    parts.failureCause(),
                    parts.repairMethod(),
                    parts.materials(),
                    parts.result(),
                    true
                );
            })
            .toList();
    }

    private RepairCaseParts buildParts(RepairTicket ticket) {
        Optional<AiTicketAnalysis> analysis = aiTicketAnalysisRepository.findByTicketId(ticket.getTicketId());
        Map<String, Object> raw = analysis.map(AiTicketAnalysis::getRawResponse).map(this::jsonMap).orElse(Map.of());
        String process = processText(ticket);
        String materials = firstText(text(raw.get("materialsUsed")), materialsText(ticket), "未记录具体材料");
        return new RepairCaseParts(
            firstText(text(raw.get("failureCause")), "根据报修描述和现场记录判断：" + firstText(ticket.getDescription(), "未填写故障描述")),
            firstText(text(raw.get("repairProcess")), process, firstText(ticket.getRepairNotes(), ticket.getProcessNotes(), "已按现场情况完成维修处理")),
            materials,
            firstText(text(raw.get("result")), resultText(ticket)),
            firstText(analysis.map(AiTicketAnalysis::getSummary).orElse(null), ticket.getRepairNotes(), ticket.getProcessNotes())
        );
    }

    private String buildDocument(RepairTicket ticket, RepairCaseParts parts) {
        return "故障描述：" + firstText(ticket.getDescription(), "未填写") +
            "\n地点：" + firstText(ticket.getLocationText(), "未填写") +
            "\n分类：" + firstText(categoryName(ticket), "未分类") +
            "\n故障原因：" + parts.failureCause() +
            "\n维修方法：" + parts.repairMethod() +
            "\n材料：" + parts.materials() +
            "\n完成总结：" + firstText(parts.summary(), "未生成") +
            "\n处理结果：" + parts.result();
    }

    private String buildQuery(RepairTicket ticket) {
        return "故障描述：" + firstText(ticket.getDescription(), "") +
            "\n地点：" + firstText(ticket.getLocationText(), "") +
            "\n分类：" + firstText(categoryName(ticket), "");
    }

    private String processText(RepairTicket ticket) {
        List<RepairProcessRecord> records = processRecordRepository.findByTicketOrderByCreatedAtAsc(ticket);
        String joined = records.stream()
            .map(record -> firstText(record.getRepairDescription(), record.getContent(), record.getRemarks()))
            .filter(this::hasText)
            .reduce((left, right) -> left + "；" + right)
            .orElse("");
        return firstText(joined, ticket.getProcessNotes(), ticket.getRepairNotes());
    }

    private String materialsText(RepairTicket ticket) {
        return processRecordRepository.findByTicketOrderByCreatedAtAsc(ticket).stream()
            .map(RepairProcessRecord::getMaterialsUsed)
            .filter(this::hasText)
            .reduce((left, right) -> left + "；" + right)
            .orElse("");
    }

    private String resultText(RepairTicket ticket) {
        return switch (ticket.getStatus()) {
            case WAITING_FEEDBACK -> "学生已确认完成，等待评价";
            case FEEDBACKED -> "学生已评价，维修闭环完成";
            case CLOSED -> "工单已关闭，维修处理完成";
            default -> "维修处理已完成";
        };
    }

    private Map<String, Object> jsonMap(String value) {
        if (!hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadata(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new java.util.HashMap<>();
            map.forEach((key, val) -> result.put(String.valueOf(key), val));
            return result;
        }
        return Map.of();
    }

    private String categoryName(RepairTicket ticket) {
        return ticket.getCategory() == null ? null : ticket.getCategory().getCategoryName();
    }

    private int normalizedLimit(int limit) {
        return limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 10);
    }

    private String getInternalSecret() {
        String secret = hasText(internalSecret) ? internalSecret : System.getenv("INTERNAL_SERVICE_SECRET");
        if (!hasText(secret)) {
            throw new IllegalStateException("INTERNAL_SERVICE_SECRET 未配置");
        }
        return secret;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Long asLong(Object value) {
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RepairCaseParts(
        String failureCause,
        String repairMethod,
        String materials,
        String result,
        String summary
    ) {
    }
}
