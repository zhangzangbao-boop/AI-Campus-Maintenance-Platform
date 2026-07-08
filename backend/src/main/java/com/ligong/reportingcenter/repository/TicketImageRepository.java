package com.ligong.reportingcenter.repository;

import com.ligong.reportingcenter.domain.entity.RepairTicket;
import com.ligong.reportingcenter.domain.entity.TicketImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketImageRepository extends JpaRepository<TicketImage, Long> {
    List<TicketImage> findByTicket(RepairTicket ticket);
}

