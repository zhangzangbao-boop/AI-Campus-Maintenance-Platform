package com.ligong.reportingcenter.repository;

import com.ligong.reportingcenter.domain.entity.RepairProcessRecord;
import com.ligong.reportingcenter.domain.entity.RepairTicket;
import com.ligong.reportingcenter.domain.enums.RepairProcessActionType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairProcessRecordRepository extends JpaRepository<RepairProcessRecord, Long> {
    List<RepairProcessRecord> findByTicketOrderByCreatedAtAsc(RepairTicket ticket);
    List<RepairProcessRecord> findByActionTypeOrderByCreatedAtDesc(RepairProcessActionType actionType);
}
