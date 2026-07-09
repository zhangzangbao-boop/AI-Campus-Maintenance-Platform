package com.ligong.reportingcenter.repository;

import com.ligong.reportingcenter.domain.entity.RepairTicket;
import com.ligong.reportingcenter.domain.entity.TicketStatusLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketStatusLogRepository extends JpaRepository<TicketStatusLog, Long> {
    List<TicketStatusLog> findByTicketOrderByLogTimeAsc(RepairTicket ticket);
}

