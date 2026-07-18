package com.qiyun.opsservice.service;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.opsservice.domain.entity.Announcement;
import com.qiyun.opsservice.domain.entity.Notification;
import com.qiyun.opsservice.domain.entity.UserReference;
import com.qiyun.opsservice.domain.enums.AnnouncementPriority;
import com.qiyun.opsservice.domain.enums.AnnouncementStatus;
import com.qiyun.opsservice.domain.enums.AnnouncementType;
import com.qiyun.opsservice.domain.enums.UserRole;
import com.qiyun.opsservice.dto.AnnouncementDto;
import com.qiyun.opsservice.dto.NotificationDto;
import com.qiyun.opsservice.dto.request.AnnouncementRequest;
import com.qiyun.opsservice.repository.AnnouncementRepository;
import com.qiyun.opsservice.repository.NotificationRepository;
import com.qiyun.opsservice.repository.UserReferenceRepository;
import com.qiyun.opsservice.websocket.WebSocketPushService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final UserReferenceRepository userReferenceRepository;
    private final NotificationRepository notificationRepository;
    private final WebSocketPushService webSocketPushService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<AnnouncementDto> listVisible() {
        return announcementRepository.findVisible(AnnouncementStatus.PUBLISHED, LocalDateTime.now()).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public AnnouncementDto getVisible(Long id) {
        Announcement item = load(id);
        if (!isVisible(item, LocalDateTime.now())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "公告不存在或已过期");
        }
        return toDto(item);
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDto> adminList() {
        return announcementRepository.findAllByOrderByPinnedDescPublishTimeDescUpdatedAtDesc().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public AnnouncementDto adminGet(Long id) {
        return toDto(load(id));
    }

    @Transactional
    public AnnouncementDto create(AnnouncementRequest request) {
        Announcement item = new Announcement();
        apply(item, request);
        announcementRepository.save(item);
        auditLogService.record("公告管理", "新增公告", "ANNOUNCEMENT", String.valueOf(item.getAnnouncementId()), item.getTitle());
        return toDto(item);
    }

    @Transactional
    public AnnouncementDto update(Long id, AnnouncementRequest request) {
        Announcement item = load(id);
        apply(item, request);
        auditLogService.record("公告管理", "编辑公告", "ANNOUNCEMENT", String.valueOf(id), item.getTitle());
        return toDto(item);
    }

    @Transactional
    public AnnouncementDto publish(Long id) {
        Announcement item = load(id);
        item.setStatus(AnnouncementStatus.PUBLISHED);
        if (item.getPublishTime() == null || item.getPublishTime().isAfter(LocalDateTime.now())) {
            item.setPublishTime(LocalDateTime.now());
        }
        AnnouncementDto dto = toDto(item);
        auditLogService.record("公告管理", "发布公告", "ANNOUNCEMENT", String.valueOf(id), item.getTitle());
        notifyPublished(item);
        return dto;
    }

    @Transactional
    public AnnouncementDto withdraw(Long id) {
        Announcement item = load(id);
        item.setStatus(AnnouncementStatus.WITHDRAWN);
        auditLogService.record("公告管理", "撤回公告", "ANNOUNCEMENT", String.valueOf(id), item.getTitle());
        return toDto(item);
    }

    @Transactional
    public void delete(Long id) {
        Announcement item = load(id);
        announcementRepository.delete(item);
        auditLogService.record("公告管理", "删除公告", "ANNOUNCEMENT", String.valueOf(id), item.getTitle());
    }

    private Announcement load(Long id) {
        return announcementRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "公告不存在"));
    }

    private void apply(Announcement item, AnnouncementRequest request) {
        item.setTitle(request.title().trim());
        item.setContent(request.content().trim());
        item.setType(request.type() == null ? AnnouncementType.GENERAL : request.type());
        item.setPriority(request.priority() == null ? AnnouncementPriority.NORMAL : request.priority());
        item.setPublishTime(request.publishTime());
        item.setExpireTime(request.expireTime());
        item.setPinned(Boolean.TRUE.equals(request.pinned()));
        if (item.getExpireTime() != null && item.getPublishTime() != null && !item.getExpireTime().isAfter(item.getPublishTime())) {
            throw new BusinessException("到期时间必须晚于发布时间");
        }
    }

    private boolean isVisible(Announcement item, LocalDateTime now) {
        return item.getStatus() == AnnouncementStatus.PUBLISHED
            && (item.getPublishTime() == null || !item.getPublishTime().isAfter(now))
            && (item.getExpireTime() == null || item.getExpireTime().isAfter(now));
    }

    private void notifyPublished(Announcement item) {
        try {
            List<UserReference> receivers = userReferenceRepository.findByRoleInAndIsActiveTrue(List.of(UserRole.STUDENT, UserRole.STAFF));
            if (receivers.isEmpty()) {
                return;
            }
            List<Notification> notifications = receivers.stream()
                .map(user -> buildNotification(user, item))
                .toList();
            notificationRepository.saveAll(notifications);
            NotificationDto pushDto = new NotificationDto(
                null,
                null,
                "校园后勤公告：" + item.getTitle(),
                preview(item.getContent()),
                null,
                false,
                LocalDateTime.now()
            );
            webSocketPushService.pushNotificationToMany(
                receivers.stream().map(UserReference::getUserId).toList(),
                pushDto
            );
        } catch (Exception e) {
            log.warn("公告发布提醒失败，不影响发布: announcementId={}, error={}", item.getAnnouncementId(), e.getMessage());
        }
    }

    private Notification buildNotification(UserReference receiver, Announcement item) {
        Notification notification = new Notification();
        notification.setReceiver(receiver);
        notification.setTitle("校园后勤公告：" + item.getTitle());
        notification.setContent(preview(item.getContent()));
        notification.setRelatedOrderId(null);
        notification.setReadFlag(false);
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }

    private AnnouncementDto toDto(Announcement item) {
        return new AnnouncementDto(
            item.getAnnouncementId(),
            item.getTitle(),
            item.getContent(),
            item.getType(),
            item.getPriority(),
            item.getStatus(),
            item.getPublishTime(),
            item.getExpireTime(),
            Boolean.TRUE.equals(item.getPinned()),
            item.getCreatedAt(),
            item.getUpdatedAt()
        );
    }
}
