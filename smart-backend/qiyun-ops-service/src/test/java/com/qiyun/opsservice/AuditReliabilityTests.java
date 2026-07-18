package com.qiyun.opsservice;

import com.qiyun.feign.client.OpsServiceClient;
import com.qiyun.opsservice.controller.InternalAuditLogController;
import com.qiyun.opsservice.domain.entity.AuditLog;
import com.qiyun.opsservice.repository.AuditLogRepository;
import com.qiyun.opsservice.repository.UserReferenceRepository;
import com.qiyun.opsservice.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditReliabilityTests {

    @Test
    void auditDetailRedactsSensitiveContent() {
        AuditLogService service = new AuditLogService(mock(AuditLogRepository.class), mock(UserReferenceRepository.class));

        String detail = service.sanitizeDetail("password=abc token=def raw_response={secret} 普通摘要");

        assertEquals("[REDACTED_SENSITIVE_AUDIT_DETAIL]", detail);
        assertFalse(detail.contains("abc"));
        assertFalse(detail.contains("raw_response"));
    }

    @Test
    void internalAuditEndpointRequiresSecretAndSavesSanitizedRecord() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogService service = new AuditLogService(repository, mock(UserReferenceRepository.class));
        InternalAuditLogController controller = new InternalAuditLogController(service, "test-secret");

        var request = new OpsServiceClient.AuditEventRequest(
            "admin01", "用户管理", "重置凭证", "USER", "u01", "token=abc", true
        );

        assertEquals(HttpStatus.UNAUTHORIZED, controller.recordAuditLog("bad", request).getStatusCode());
        assertEquals(HttpStatus.OK, controller.recordAuditLog("test-secret", request).getStatusCode());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertEquals("用户管理", captor.getValue().getModule());
        assertEquals("[REDACTED_SENSITIVE_AUDIT_DETAIL]", captor.getValue().getDetail());
        assertTrue(captor.getValue().getSuccess());
    }
}
