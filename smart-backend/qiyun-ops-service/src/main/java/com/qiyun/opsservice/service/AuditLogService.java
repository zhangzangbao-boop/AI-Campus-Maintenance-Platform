package com.qiyun.opsservice.service;

import com.qiyun.opsservice.domain.entity.AuditLog;
import com.qiyun.opsservice.domain.entity.UserReference;
import com.qiyun.opsservice.dto.AuditLogDto;
import com.qiyun.opsservice.repository.AuditLogRepository;
import com.qiyun.opsservice.repository.UserReferenceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.regex.Pattern;
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
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
        "(?i)(password|passwd|token|secret|api[-_ ]?key|access[-_ ]?key|raw_response|prompt|authorization|密码|口令|密钥|令牌|内部提示词)"
            + "\\s*[:=]\\s*(\"[^\"]*\"|'[^']*'|[^,;\\s}]+)"
    );
    private static final Pattern SENSITIVE_WORD_PATTERN = Pattern.compile(
        "(?i)(password|passwd|token|secret|api[-_ ]?key|access[-_ ]?key|raw_response|prompt|authorization|密码|口令|密钥|令牌|内部提示词)"
    );

    @Transactional
    public void record(String module, String action, String targetType, String targetId, String detail) {
        record(module, action, targetType, targetId, detail, null, true);
    }

    @Transactional
    public void record(String module, String action, String targetType, String targetId, String detail,
                       HttpServletRequest request, boolean success) {
        record(currentUserIdOrNull(), module, action, targetType, targetId, detail, request, success);
    }

    @Transactional
    public void recordExternal(String actorId, String module, String action, String targetType, String targetId,
                               String detail, boolean success) {
        record(actorId, module, action, targetType, targetId, detail, null, success);
    }

    private void record(String actorId, String module, String action, String targetType, String targetId, String detail,
                        HttpServletRequest request, boolean success) {
        AuditLog log = new AuditLog();
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
        log.setDetail(sanitizeDetail(detail));
        log.setSuccess(success);
        if (request != null) {
            log.setRequestMethod(request.getMethod());
            log.setRequestPath(request.getRequestURI());
            log.setIpAddress(request.getRemoteAddr());
        }
        auditLogRepository.save(log);
    }

    public String sanitizeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return detail;
        }
        String sanitized = SENSITIVE_FIELD_PATTERN.matcher(detail).replaceAll("$1=[REDACTED]");
        if (SENSITIVE_WORD_PATTERN.matcher(sanitized).find()) {
            return "[REDACTED_SENSITIVE_AUDIT_DETAIL]";
        }
        return sanitized.length() > 2000 ? sanitized.substring(0, 2000) + "..." : sanitized;
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
