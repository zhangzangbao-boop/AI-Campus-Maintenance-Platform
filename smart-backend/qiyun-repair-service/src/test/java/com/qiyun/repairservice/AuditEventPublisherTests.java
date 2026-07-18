package com.qiyun.repairservice;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.feign.client.OpsServiceClient;
import com.qiyun.repairservice.service.AuditEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class AuditEventPublisherTests {

    @Test
    void opsAuditFailureDoesNotBreakRepairFlow() {
        OpsServiceClient client = mock(OpsServiceClient.class);
        when(client.recordAuditLog(anyString(), any())).thenThrow(new RuntimeException("ops down"));
        AuditEventPublisher publisher = new AuditEventPublisher(client);
        ReflectionTestUtils.setField(publisher, "internalSecret", "test-secret");

        assertDoesNotThrow(() -> publisher.record(
            "admin01", "工单管理", "修改状态", "REPAIR_TICKET", "100", "状态 WAITING_ACCEPT -> IN_PROGRESS"
        ));

        verify(client).recordAuditLog(eq("test-secret"), any(OpsServiceClient.AuditEventRequest.class));
    }
}
