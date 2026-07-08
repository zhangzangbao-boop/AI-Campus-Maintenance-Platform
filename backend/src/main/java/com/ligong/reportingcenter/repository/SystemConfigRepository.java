package com.ligong.reportingcenter.repository;

import com.ligong.reportingcenter.domain.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {
}
