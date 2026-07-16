package com.qiyun.repairservice.repository;

import com.qiyun.repairservice.domain.entity.SlaAlert;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * SLA告警Repository
 */
@Repository
public interface SlaAlertRepository extends JpaRepository<SlaAlert, Long> {

    /**
     * 查找工单的指定级别告警记录
     * 用于判断是否已经发送过告警（去重）
     *
     * @param ticketId   工单ID
     * @param alertLevel 告警级别（WARNING/OVERDUE）
     * @return 告警记录
     */
    Optional<SlaAlert> findByTicketIdAndAlertLevel(Long ticketId, String alertLevel);

    /**
     * 检查工单是否存在指定级别的告警记录
     *
     * @param ticketId   工单ID
     * @param alertLevel 告警级别
     * @return 是否存在
     */
    boolean existsByTicketIdAndAlertLevel(Long ticketId, String alertLevel);
}