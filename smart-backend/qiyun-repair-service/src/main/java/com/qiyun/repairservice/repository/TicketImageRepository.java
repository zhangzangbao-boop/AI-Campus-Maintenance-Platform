package com.qiyun.repairservice.repository;

import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.TicketImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketImageRepository extends JpaRepository<TicketImage, Long> {
    List<TicketImage> findByTicket(RepairTicket ticket);
}