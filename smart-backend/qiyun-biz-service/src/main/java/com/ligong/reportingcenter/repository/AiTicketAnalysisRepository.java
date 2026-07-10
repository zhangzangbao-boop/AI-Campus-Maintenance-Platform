package com.ligong.reportingcenter.repository;

import com.ligong.reportingcenter.domain.entity.AiTicketAnalysis;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTicketAnalysisRepository extends JpaRepository<AiTicketAnalysis, Long> {

    /**
     * 根据工单ID查找AI分析结果
     */
    Optional<AiTicketAnalysis> findByTicketId(Long ticketId);
}
