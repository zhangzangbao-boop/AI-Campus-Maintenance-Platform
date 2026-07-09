package com.ligong.reportingcenter.repository;

import com.ligong.reportingcenter.domain.entity.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a " +
           "LEFT JOIN FETCH a.actor " +
           "WHERE (:keyword IS NULL OR :keyword = '' " +
           "   OR LOWER(a.action) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "   OR LOWER(a.module) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "   OR LOWER(a.targetId) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "   OR LOWER(a.detail) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY a.createdAt DESC")
    List<AuditLog> search(@Param("keyword") String keyword);
}
