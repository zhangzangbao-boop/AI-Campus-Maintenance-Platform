package com.ligong.reportingcenter.repository;

import com.ligong.reportingcenter.domain.entity.RepairTicket;
import com.ligong.reportingcenter.domain.entity.TicketComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
    List<TicketComment> findByTicketOrderByCreatedAtAsc(RepairTicket ticket);
}
