package com.qiyun.repairservice.repository;

import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.TicketStatusLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketStatusLogRepository extends JpaRepository<TicketStatusLog, Long> {
    List<TicketStatusLog> findByTicketOrderByLogTimeAsc(RepairTicket ticket);
}