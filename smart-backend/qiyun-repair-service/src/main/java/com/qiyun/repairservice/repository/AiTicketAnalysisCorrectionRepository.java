package com.qiyun.repairservice.repository;

import com.qiyun.repairservice.domain.entity.AiTicketAnalysisCorrection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTicketAnalysisCorrectionRepository extends JpaRepository<AiTicketAnalysisCorrection, Long> {

    List<AiTicketAnalysisCorrection> findByAnalysisIdOrderByCorrectedAtAsc(Long analysisId);

    List<AiTicketAnalysisCorrection> findByTicketIdOrderByCorrectedAtAsc(Long ticketId);
}
