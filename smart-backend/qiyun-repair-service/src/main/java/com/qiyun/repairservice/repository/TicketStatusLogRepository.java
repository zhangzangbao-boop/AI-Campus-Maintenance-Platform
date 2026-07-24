package com.qiyun.repairservice.repository;

import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.TicketStatusLog;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketStatusLogRepository extends JpaRepository<TicketStatusLog, Long> {
    List<TicketStatusLog> findByTicketOrderByLogTimeAsc(RepairTicket ticket);

    boolean existsByTicketAndOldStatusAndNewStatusAndLogTimeGreaterThanEqualAndLogTimeLessThan(
        RepairTicket ticket,
        TicketStatus oldStatus,
        TicketStatus newStatus,
        LocalDateTime start,
        LocalDateTime end
    );
}
