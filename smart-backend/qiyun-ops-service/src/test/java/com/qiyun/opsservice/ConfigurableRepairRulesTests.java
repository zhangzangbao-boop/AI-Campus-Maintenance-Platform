package com.qiyun.opsservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.common.exception.BusinessException;
import com.qiyun.opsservice.controller.InternalSystemConfigController;
import com.qiyun.opsservice.domain.entity.SystemConfig;
import com.qiyun.opsservice.dto.request.SystemConfigRequest;
import com.qiyun.opsservice.repository.SystemConfigRepository;
import com.qiyun.opsservice.repository.UserReferenceRepository;
import com.qiyun.opsservice.service.AuditLogService;
import com.qiyun.opsservice.service.SystemConfigService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigurableRepairRulesTests {

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @Mock
    private UserReferenceRepository userReferenceRepository;

    @Mock
    private AuditLogService auditLogService;

    @Test
    void internalRuleEndpointRequiresSecret() {
        SystemConfigService service = mock(SystemConfigService.class);
        InternalSystemConfigController controller = new InternalSystemConfigController(service, "test-secret");

        assertEquals(HttpStatus.UNAUTHORIZED, controller.getRepairRuleConfig("wrong").getStatusCode());
        assertEquals(HttpStatus.OK, controller.getRepairRuleConfig("test-secret").getStatusCode());
        verify(service).repairRuleConfig();
    }

    @Test
    void invalidStructuredConfigIsRejected() {
        SystemConfigService service = new SystemConfigService(
            systemConfigRepository,
            userReferenceRepository,
            auditLogService,
            new ObjectMapper()
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.save(
            SystemConfigService.SLA_RULES,
            new SystemConfigRequest("{\"warningRatio\":1.5}", "bad"),
            "admin01"
        ));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(systemConfigRepository, never()).save(any());
    }

    @Test
    void validStructuredConfigRecordsAuditBeforeAndAfter() {
        SystemConfig existing = new SystemConfig();
        existing.setConfigKey(SystemConfigService.FAULT_TREND_RULES);
        existing.setConfigValue("""
            {"sevenDays":{"countThreshold":3,"growthThreshold":50},"thirtyDays":{"countThreshold":6,"growthThreshold":30}}
            """);
        when(systemConfigRepository.findById(SystemConfigService.FAULT_TREND_RULES)).thenReturn(Optional.of(existing));
        when(userReferenceRepository.findByUserIdAndIsActiveTrue("admin01")).thenReturn(Optional.empty());
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        SystemConfigService service = new SystemConfigService(
            systemConfigRepository,
            userReferenceRepository,
            auditLogService,
            new ObjectMapper()
        );

        service.save(
            SystemConfigService.FAULT_TREND_RULES,
            new SystemConfigRequest("""
                {"sevenDays":{"countThreshold":4,"growthThreshold":60},"thirtyDays":{"countThreshold":8,"growthThreshold":40}}
                """, "rules"),
            "admin01"
        );

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq("系统配置"), eq("更新配置"), eq("SYSTEM_CONFIG"),
            eq(SystemConfigService.FAULT_TREND_RULES), detailCaptor.capture());
        String detail = detailCaptor.getValue();
        assertTrue(detail.contains("修改人=admin01"));
        assertTrue(detail.contains("修改前="));
        assertTrue(detail.contains("修改后="));
        assertTrue(detail.contains("countThreshold\":4"));
    }

    @Test
    void repairRuleConfigMergesDefaults() {
        when(systemConfigRepository.findById(anyString())).thenReturn(Optional.empty());
        SystemConfigService service = new SystemConfigService(
            systemConfigRepository,
            userReferenceRepository,
            auditLogService,
            new ObjectMapper()
        );

        Map<String, String> rules = service.repairRuleConfig();

        assertTrue(rules.get(SystemConfigService.AI_CATEGORY_KEYWORDS).contains("空调故障"));
        assertTrue(rules.get(SystemConfigService.AI_URGENCY_RULES).contains("紧急"));
        assertTrue(rules.get(SystemConfigService.SLA_RULES).contains("\"warningRatio\":0.25"));
        assertTrue(rules.get(SystemConfigService.FAULT_TREND_RULES).contains("\"countThreshold\":3"));
    }
}
