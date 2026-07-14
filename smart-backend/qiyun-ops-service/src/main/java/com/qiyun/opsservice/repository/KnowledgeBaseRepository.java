package com.qiyun.opsservice.repository;

import com.qiyun.opsservice.domain.entity.KnowledgeBase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    @Query("SELECT k FROM KnowledgeBase k " +
           "WHERE k.enabled = true " +
           "AND (:categoryKey IS NULL OR :categoryKey = '' OR k.categoryKey = :categoryKey) " +
           "AND (:keyword IS NULL OR :keyword = '' " +
           "  OR LOWER(k.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(k.symptomKeywords) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(k.solutionSteps) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY k.updatedAt DESC")
    List<KnowledgeBase> searchEnabled(@Param("categoryKey") String categoryKey,
                                       @Param("keyword") String keyword);

    @Query("SELECT k FROM KnowledgeBase k " +
           "WHERE k.enabled = true " +
           "AND (:categoryKey IS NULL OR :categoryKey = '' OR k.categoryKey = :categoryKey) " +
           "ORDER BY k.updatedAt DESC")
    List<KnowledgeBase> findEnabledByCategory(@Param("categoryKey") String categoryKey);

    @Query("SELECT k FROM KnowledgeBase k ORDER BY k.updatedAt DESC")
    List<KnowledgeBase> findAllWithCategory();
}