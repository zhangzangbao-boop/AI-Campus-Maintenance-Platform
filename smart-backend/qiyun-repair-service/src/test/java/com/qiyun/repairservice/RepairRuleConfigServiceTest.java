package com.qiyun.repairservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.feign.client.OpsServiceClient;
import com.qiyun.repairservice.service.RepairRuleConfigService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepairRuleConfigServiceTest {

    @Test
    void opsUnavailableFallsBackToDefaultSlaRules() {
        OpsServiceClient opsServiceClient = mock(OpsServiceClient.class);
        when(opsServiceClient.getRepairRuleConfig("test-secret")).thenThrow(new RuntimeException("down"));
        RepairRuleConfigService service = new RepairRuleConfigService(opsServiceClient, new ObjectMapper());
        ReflectionTestUtils.setField(service, "configuredInternalSecret", "test-secret");

        RepairRuleConfigService.SlaRules rules = service.slaRules();

        assertEquals(0.25, rules.warningRatio());
        assertEquals(8, rules.priority("medium").responseHours());
        assertEquals(72, rules.priority("medium").completionHours());
    }

    @Test
    void shortCacheKeepsRulesUntilClearedThenReloads() {
        OpsServiceClient opsServiceClient = mock(OpsServiceClient.class);
        when(opsServiceClient.getRepairRuleConfig("test-secret"))
            .thenReturn(responseWithSla(4))
            .thenReturn(responseWithSla(6));
        RepairRuleConfigService service = new RepairRuleConfigService(opsServiceClient, new ObjectMapper());
        ReflectionTestUtils.setField(service, "configuredInternalSecret", "test-secret");

        assertEquals(4, service.slaRules().priority("medium").responseHours());
        assertEquals(4, service.slaRules().priority("medium").responseHours());
        verify(opsServiceClient, times(1)).getRepairRuleConfig("test-secret");

        service.clearCacheForTest();
        assertEquals(6, service.slaRules().priority("medium").responseHours());
        verify(opsServiceClient, times(2)).getRepairRuleConfig("test-secret");
    }

    private Map<String, Object> responseWithSla(int mediumResponseHours) {
        return Map.of(
            "code", 200,
            "data", Map.of("sla.ticket.rules", """
                {
                  "warningRatio":0.25,
                  "priorities":{
                    "high":{"responseHours":2,"completionHours":24},
                    "medium":{"responseHours":%d,"completionHours":72},
                    "low":{"responseHours":24,"completionHours":168}
                  }
                }
                """.formatted(mediumResponseHours))
        );
    }
}
