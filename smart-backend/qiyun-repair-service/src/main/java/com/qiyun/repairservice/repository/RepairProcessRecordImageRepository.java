package com.qiyun.repairservice.repository;

import com.qiyun.repairservice.domain.entity.RepairProcessRecord;
import com.qiyun.repairservice.domain.entity.RepairProcessRecordImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairProcessRecordImageRepository extends JpaRepository<RepairProcessRecordImage, Long> {
    List<RepairProcessRecordImage> findByRecordOrderByImageIdAsc(RepairProcessRecord record);
}
