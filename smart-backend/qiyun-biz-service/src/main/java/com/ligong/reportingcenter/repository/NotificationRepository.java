package com.ligong.reportingcenter.repository;

import com.ligong.reportingcenter.domain.entity.Notification;
import com.ligong.reportingcenter.domain.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByReceiverOrderByCreatedAtDesc(User receiver);

    long countByReceiverAndReadFlagFalse(User receiver);

    Optional<Notification> findByNotificationIdAndReceiver(Long notificationId, User receiver);
}
