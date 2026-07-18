package com.qiyun.opsservice;

import com.qiyun.feign.client.RepairServiceClient;
import com.qiyun.opsservice.service.AdminExportService;
import com.qiyun.opsservice.service.AuditLogService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminExportServiceTests {

    private final RepairServiceClient repairServiceClient = mock(RepairServiceClient.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AdminExportService service = new AdminExportService(repairServiceClient, auditLogService);

    @Test
    void csvEscapesChineseCommaNewlineAndFormulaInjection() {
        byte[] bytes = service.csvBytes(List.of("标题", "内容"), List.of(List.of("中文,标题", "=SUM(1,2)\n下一行")));
        String csv = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF);
        assertTrue(csv.contains("\"中文,标题\""));
        assertTrue(csv.contains("\"'=SUM(1,2)\n下一行\""));
    }

    @Test
    void exportTicketsPassesFiltersLimitsRowsAndAudits() {
        List<Map<String, Object>> tickets = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ticketId", i + 1);
            item.put("status", "WAITING_ACCEPT");
            item.put("categoryName", "管道故障");
            item.put("description", "水管,漏水");
            tickets.add(item);
        }
        when(repairServiceClient.getTicketsForExport(any(), eq("pending"), eq("管道故障"), eq("漏水"),
            eq(false), eq("2026-07-01"), eq("2026-07-18"), eq(AdminExportService.MAX_EXPORT_ROWS)))
            .thenReturn(Map.of("data", Map.of("list", tickets, "total", 2)));

        AdminExportService.ExportFile file = service.exportTickets("Bearer token", Map.of(
            "status", "pending",
            "category", "管道故障",
            "keyword", "漏水",
            "startDate", "2026-07-01",
            "endDate", "2026-07-18"
        ), "admin01");

        assertEquals(2, file.rowCount());
        verify(auditLogService).record(eq("数据导出"), eq("导出工单明细"), eq("ADMIN_EXPORT"),
            eq("工单明细"), contains("结果数量=2"));
    }

    @Test
    void exportFeedbacksUsesMaxLimit() {
        when(repairServiceClient.getFeedbacks(any(), eq(0), eq(AdminExportService.MAX_EXPORT_ROWS),
            eq(true), eq("NEGATIVE"), eq("PENDING"), isNull(), isNull()))
            .thenReturn(Map.of("data", Map.of("list", List.of(Map.of("ratingId", 1, "comment", "差")), "total", 1)));

        AdminExportService.ExportFile file = service.exportFeedbacks("Bearer token", Map.of(
            "lowRating", "true",
            "sentiment", "NEGATIVE",
            "followUpStatus", "PENDING"
        ), "admin01");

        assertEquals(1, file.rowCount());
        verify(auditLogService).record(eq("数据导出"), eq("导出评价与回访记录"), eq("ADMIN_EXPORT"),
            eq("评价与回访记录"), contains("数量上限=5000"));
    }
}
