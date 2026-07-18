package com.qiyun.opsservice.repository;

import com.qiyun.opsservice.domain.entity.FaultTrendAlert;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaultTrendAlertRepository extends JpaRepository<FaultTrendAlert, Long> {

    Optional<FaultTrendAlert> findByLocationAndCategoryAndPeriodDays(String location, String category, Integer periodDays);

    List<FaultTrendAlert> findAllByOrderByRiskLevelDescLastDetectedAtDesc();
}
