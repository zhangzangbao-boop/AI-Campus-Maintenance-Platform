package com.qiyun.opsservice.repository;

import com.qiyun.opsservice.domain.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {
}