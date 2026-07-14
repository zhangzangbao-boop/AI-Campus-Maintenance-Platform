package com.qiyun.repairservice.repository;

import com.qiyun.repairservice.domain.entity.RepairProcessRecord;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.enums.RepairProcessActionType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairProcessRecordRepository extends JpaRepository<RepairProcessRecord, Long> {
    List<RepairProcessRecord> findByTicketOrderByCreatedAtAsc(RepairTicket ticket);
    List<RepairProcessRecord> findByActionTypeOrderByCreatedAtDesc(RepairProcessActionType actionType);
}