package com.qiyun.opsservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.feign.client.RepairServiceClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminExportService {

    public static final int MAX_EXPORT_ROWS = 5000;
    private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final RepairServiceClient repairServiceClient;
    private final AuditLogService auditLogService;

    public ExportFile exportTickets(String authorization, Map<String, String> filters, String operatorId) {
        Map<String, Object> response = repairServiceClient.getTicketsForExport(
            authorization,
            filters.get("status"),
            filters.get("category"),
            filters.get("keyword"),
            Boolean.parseBoolean(filters.getOrDefault("includeDeleted", "false")),
            filters.get("startDate"),
            filters.get("endDate"),
            MAX_EXPORT_ROWS
        );
        List<Map<String, Object>> rows = listFromResponse(response);
        byte[] content = csvBytes(List.of("工单ID", "状态", "分类", "学生ID", "维修工ID", "地点", "描述", "紧急程度", "创建时间", "分配时间", "预计完成时间", "评分", "已删除", "删除时间"),
            rows.stream().map(item -> List.of(
                value(item, "ticketId"),
                value(item, "status"),
                value(item, "categoryName"),
                value(item, "studentId"),
                value(item, "staffId"),
                value(item, "locationText"),
                value(item, "description"),
                value(item, "priority"),
                value(item, "createdAt"),
                value(item, "assignedAt"),
                value(item, "estimatedCompletionTime"),
                value(item, "ratingScore"),
                value(item, "deleted"),
                value(item, "deletedAt")
            )).toList());
        recordAudit("工单明细", filters, operatorId, rows.size());
        return new ExportFile(filename("tickets"), content, rows.size());
    }

    public ExportFile exportFeedbacks(String authorization, Map<String, String> filters, String operatorId) {
        Map<String, Object> response = repairServiceClient.getFeedbacks(
            authorization,
            0,
            MAX_EXPORT_ROWS,
            Boolean.parseBoolean(filters.getOrDefault("lowRating", "false")),
            filters.get("sentiment"),
            filters.get("followUpStatus"),
            filters.get("startDate"),
            filters.get("endDate")
        );
        List<Map<String, Object>> rows = listFromResponse(response);
        byte[] content = csvBytes(List.of("评价ID", "工单ID", "评分", "评价内容", "学生ID", "学生姓名", "维修工ID", "维修工姓名", "速度评分", "质量评分", "态度评分", "是否解决", "匿名", "评价时间", "情绪", "情绪分", "关键词", "AI摘要", "回访状态", "回访记录", "回访人", "回访时间"),
            rows.stream().map(item -> List.of(
                value(item, "ratingId"),
                value(item, "repairOrderId"),
                value(item, "score"),
                value(item, "comment"),
                value(item, "studentId"),
                value(item, "studentName"),
                value(item, "staffId"),
                value(item, "staffName"),
                value(item, "speedRating"),
                value(item, "qualityRating"),
                value(item, "attitudeRating"),
                value(item, "resolved"),
                value(item, "anonymous"),
                value(item, "ratedAt"),
                value(item, "sentiment"),
                value(item, "sentimentScore"),
                value(item, "sentimentKeywords"),
                value(item, "sentimentSummary"),
                value(item, "followUpStatus"),
                value(item, "followUpNote"),
                value(item, "followUpOperatorId"),
                value(item, "followUpUpdatedAt")
            )).toList());
        recordAudit("评价与回访记录", filters, operatorId, rows.size());
        return new ExportFile(filename("feedbacks"), content, rows.size());
    }

    public ExportFile exportStats(String authorization, Map<String, String> filters, String operatorId) {
        List<List<String>> rows = new ArrayList<>();
        appendStats(rows, "工单状态统计", repairServiceClient.getStatusStats(authorization));
        appendStats(rows, "工单分类统计", repairServiceClient.getCategoryStats(authorization));
        appendStats(rows, "地点统计", repairServiceClient.getLocationStats(authorization));
        appendStats(rows, "月度统计", repairServiceClient.getMonthlyStats(authorization));
        byte[] content = csvBytes(List.of("统计类型", "维度/字段", "值", "原始记录"), rows);
        recordAudit("工单统计", filters, operatorId, rows.size());
        return new ExportFile(filename("stats"), content, rows.size());
    }

    public ExportFile export(String type, String authorization, Map<String, String> filters, String operatorId) {
        return switch (type) {
            case "tickets" -> exportTickets(authorization, filters, operatorId);
            case "feedbacks" -> exportFeedbacks(authorization, filters, operatorId);
            case "stats" -> exportStats(authorization, filters, operatorId);
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的导出类型");
        };
    }

    private void appendStats(List<List<String>> rows, String type, Map<String, Object> response) {
        Object data = response == null ? null : response.get("data");
        if (data instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> map = asMap(item);
                rows.add(List.of(type, firstNonBlank(map, "status", "category", "name", "location", "month", "id"), firstNonBlank(map, "count", "value", "totalTickets"), String.valueOf(item)));
            }
            return;
        }
        if (data instanceof Map<?, ?> mapData) {
            for (Map.Entry<?, ?> entry : mapData.entrySet()) {
                rows.add(List.of(type, String.valueOf(entry.getKey()), String.valueOf(entry.getValue()), String.valueOf(mapData)));
            }
        }
    }

    private List<Map<String, Object>> listFromResponse(Map<String, Object> response) {
        Object data = response == null ? null : response.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object list = dataMap.get("list");
            if (list instanceof List<?> items) {
                if (items.size() > MAX_EXPORT_ROWS) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "导出数量超过上限");
                }
                return items.stream().map(this::asMap).toList();
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object item) {
        if (item instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return new LinkedHashMap<>();
    }

    public byte[] csvBytes(List<String> headers, List<List<String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append(toCsvLine(headers));
        for (List<String> row : rows) {
            builder.append(toCsvLine(row));
        }
        byte[] csv = builder.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + csv.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(csv, 0, result, UTF8_BOM.length, csv.length);
        return result;
    }

    private String toCsvLine(List<String> values) {
        return values.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse("") + "\r\n";
    }

    String escapeCsv(String raw) {
        String value = raw == null ? "" : raw;
        if (!value.isBlank() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        boolean quote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        value = value.replace("\"", "\"\"");
        return quote ? "\"" + value + "\"" : value;
    }

    private String value(Map<String, Object> item, String key) {
        Object value = item.get(key);
        if (value instanceof List<?> list) {
            return String.join("、", list.stream().map(String::valueOf).toList());
        }
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private void recordAudit(String exportType, Map<String, String> filters, String operatorId, int count) {
        auditLogService.record("数据导出", "导出" + exportType, "ADMIN_EXPORT", exportType,
            "导出人=" + operatorId
                + "；导出类型=" + exportType
                + "；条件=" + filters
                + "；导出时间=" + LocalDateTime.now()
                + "；结果数量=" + count
                + "；数量上限=" + MAX_EXPORT_ROWS);
    }

    private String filename(String type) {
        return type + "-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + ".csv";
    }

    public record ExportFile(String filename, byte[] content, int rowCount) {}
}
