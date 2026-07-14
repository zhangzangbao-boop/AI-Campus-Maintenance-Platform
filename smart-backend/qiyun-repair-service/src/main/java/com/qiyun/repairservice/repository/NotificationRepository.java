package com.qiyun.repairservice.repository;

import com.qiyun.repairservice.domain.entity.Notification;
import com.qiyun.repairservice.domain.entity.UserReference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByReceiverOrderByCreatedAtDesc(UserReference receiver);

    long countByReceiverAndReadFlagFalse(UserReference receiver);

    Optional<Notification> findByNotificationIdAndReceiver(Long notificationId, UserReference receiver);
}