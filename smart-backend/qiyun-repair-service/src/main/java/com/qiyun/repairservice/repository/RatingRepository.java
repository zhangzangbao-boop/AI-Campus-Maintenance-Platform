package com.qiyun.repairservice.repository;

import com.qiyun.repairservice.domain.entity.Rating;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    @Query("SELECT r FROM Rating r " +
           "LEFT JOIN FETCH r.student " +
           "LEFT JOIN FETCH r.staff " +
           "LEFT JOIN FETCH r.followUpOperator " +
           "LEFT JOIN FETCH r.ticket")
    List<Rating> findAllWithDetails();

    Optional<Rating> findByTicket(RepairTicket ticket);
    boolean existsByTicket(RepairTicket ticket);
}
