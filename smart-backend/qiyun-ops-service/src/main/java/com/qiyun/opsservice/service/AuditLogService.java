package com.qiyun.opsservice.service;

import com.qiyun.opsservice.domain.entity.AuditLog;
import com.qiyun.opsservice.domain.entity.UserReference;
import com.qiyun.opsservice.dto.AuditLogDto;
import com.qiyun.opsservice.repository.AuditLogRepository;
import com.qiyun.opsservice.repository.UserReferenceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserReferenceRepository userReferenceRepository;

    @Transactional
    public void record(String module, String action, String targetType, String targetId, String detail) {
        record(module, action, targetType, targetId, detail, null, true);
    }

    @Transactional
    public void record(String module, String action, String targetType, String targetId, String detail,
                       HttpServletRequest request, boolean success) {
        AuditLog log = new AuditLog();
        String actorId = currentUserIdOrNull();
        if (actorId != null) {
            try {
                UserReference actor = userReferenceRepository.findByUserIdAndIsActiveTrue(actorId).orElse(null);
                if (actor != null) {
                    log.setActor(actor);
                }
            } catch (Exception ignored) {
                // 审计日志不能影响主流程
            }
        }
        log.setModule(module);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setSuccess(success);
        if (request != null) {
            log.setRequestMethod(request.getMethod());
            log.setRequestPath(request.getRequestURI());
            log.setIpAddress(request.getRemoteAddr());
        }
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> list(String keyword, int limit) {
        int max = limit <= 0 ? 100 : Math.min(limit, 300);
        return auditLogRepository.search(keyword).stream()
            .limit(max)
            .map(this::toDto)
            .toList();
    }

    private String currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return authentication.getName();
    }

    private AuditLogDto toDto(AuditLog log) {
        return new AuditLogDto(
            log.getAuditId(),
            log.getActor() != null ? log.getActor().getUserId() : null,
            log.getActor() != null ? log.getActor().getNickname() : null,
            log.getAction(),
            log.getModule(),
            log.getTargetType(),
            log.getTargetId(),
            log.getDetail(),
            log.getRequestMethod(),
            log.getRequestPath(),
            log.getSuccess(),
            log.getIpAddress(),
            log.getCreatedAt()
        );
    }
}