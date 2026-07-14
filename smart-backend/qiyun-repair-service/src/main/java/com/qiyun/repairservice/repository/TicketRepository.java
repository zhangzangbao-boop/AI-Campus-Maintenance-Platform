package com.qiyun.repairservice.repository;

import com.qiyun.repairservice.domain.entity.Category;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.entity.Rating;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<RepairTicket, Long> {
    // 查询未删除的工单
    @Query("SELECT rt FROM RepairTicket rt WHERE rt.student = :student AND (rt.deleted IS NULL OR rt.deleted = false)")
    List<RepairTicket> findByStudent(@Param("student") UserReference student);

    @Query("SELECT rt FROM RepairTicket rt WHERE rt.staff = :staff AND (rt.deleted IS NULL OR rt.deleted = false)")
    List<RepairTicket> findByStaff(@Param("staff") UserReference staff);

    @Query("SELECT rt FROM RepairTicket rt WHERE rt.status = :status AND (rt.deleted IS NULL OR rt.deleted = false)")
    List<RepairTicket> findByStatus(@Param("status") TicketStatus status);

    // 查询所有工单（包括已删除的，供管理员使用）
    @Query("SELECT rt FROM RepairTicket rt WHERE rt.student = :student")
    List<RepairTicket> findByStudentIncludeDeleted(@Param("student") UserReference student);

    @Query("SELECT rt FROM RepairTicket rt WHERE rt.staff = :staff")
    List<RepairTicket> findByStaffIncludeDeleted(@Param("staff") UserReference staff);

    @Query("SELECT rt FROM RepairTicket rt WHERE rt.status = :status")
    List<RepairTicket> findByStatusIncludeDeleted(@Param("status") TicketStatus status);

    @Query("SELECT COUNT(rt) FROM RepairTicket rt WHERE rt.category.categoryId = :categoryId AND (rt.deleted IS NULL OR rt.deleted = false)")
    Long countByCategoryId(@Param("categoryId") Long categoryId);

    @Query("SELECT COUNT(rt) FROM RepairTicket rt WHERE FUNCTION('DATE_FORMAT', rt.createdAt, '%Y-%m') = :monthPattern AND (rt.deleted IS NULL OR rt.deleted = false)")
    Long countByMonth(@Param("monthPattern") String monthPattern);

    @Query("SELECT COUNT(rt) FROM RepairTicket rt WHERE rt.status = :status AND (rt.deleted IS NULL OR rt.deleted = false)")
    Long countByStatus(@Param("status") TicketStatus status);

    // 新增方法：获取位置报修数量统计（排除已删除）
    @Query("SELECT t.locationText, COUNT(t.id) FROM RepairTicket t WHERE (t.deleted IS NULL OR t.deleted = false) GROUP BY t.locationText ORDER BY COUNT(t.id) DESC")
    List<Object[]> findLocationStats();

    // 获取按类别的统计信息（动态查询真实数据）
    @Query(
        value = "SELECT " +
                "  c.category_key, " +
                "  c.category_key AS category_name, " +
                "  COUNT(t.id) AS total_tickets, " +
                "  SUM(CASE WHEN t.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN 1 ELSE 0 END) AS completed_tickets, " +
                "  SUM(CASE WHEN r.id IS NOT NULL THEN 1 ELSE 0 END) AS rated_tickets, " +
                "  COALESCE(AVG(r.rating), 0) AS avg_rating " +
                "FROM repair_category c " +
                "LEFT JOIN repair_order t ON t.category_key = c.category_key AND (t.is_deleted IS NULL OR t.is_deleted = false) " +
                "LEFT JOIN repair_feedback r ON r.repair_order_id = t.id " +
                "GROUP BY c.category_key " +
                "ORDER BY total_tickets DESC",
        nativeQuery = true
    )
    List<Object[]> findCategoryStatsFromView();

    // 获取维修人员评分统计（动态查询真实数据）
    @Query(
        value = "SELECT " +
                "  u.user_number AS repairman_id, " +
                "  COALESCE(u.name, u.user_number) AS repairman_name, " +
                "  COALESCE(AVG(r.rating), 0) AS avg_rating, " +
                "  COUNT(DISTINCT t.id) AS completed_tickets " +
                "FROM sys_user u " +
                "LEFT JOIN repair_order t ON t.repairman_id = u.user_number AND t.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') AND (t.is_deleted IS NULL OR t.is_deleted = false) " +
                "LEFT JOIN repair_feedback r ON r.repair_order_id = t.id AND r.repairman_id = u.user_number " +
                "WHERE u.role = 'STAFF' " +
                "GROUP BY u.user_number, u.name " +
                "HAVING completed_tickets > 0 " +
                "ORDER BY avg_rating DESC, completed_tickets DESC",
        nativeQuery = true
    )
    List<Object[]> findRepairmanRatingStats();

    @Query(
        value = "SELECT " +
                "  SUBSTRING_INDEX(TRIM(location), ' ', 1) AS area_name, " +
                "  COUNT(*) AS total_tickets, " +
                "  SUM(CASE WHEN status IN ('WAITING_ACCEPT', 'IN_PROGRESS') THEN 1 ELSE 0 END) AS active_tickets " +
                "FROM repair_order " +
                "WHERE location IS NOT NULL " +
                "  AND TRIM(location) <> '' " +
                "  AND (is_deleted IS NULL OR is_deleted = false) " +
                "GROUP BY area_name " +
                "HAVING total_tickets > 0 " +
                "ORDER BY total_tickets DESC, active_tickets DESC " +
                "LIMIT 8",
        nativeQuery = true
    )
    List<Object[]> findHotAreaStats();

    @Query(
        value = "SELECT " +
                "  category_key, " +
                "  SUM(CASE WHEN created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) AS recent_count, " +
                "  SUM(CASE WHEN created_at < DATE_SUB(NOW(), INTERVAL 7 DAY) AND created_at >= DATE_SUB(NOW(), INTERVAL 14 DAY) THEN 1 ELSE 0 END) AS previous_count " +
                "FROM repair_order " +
                "WHERE category_key IS NOT NULL " +
                "  AND created_at >= DATE_SUB(NOW(), INTERVAL 14 DAY) " +
                "  AND (is_deleted IS NULL OR is_deleted = false) " +
                "GROUP BY category_key " +
                "ORDER BY (recent_count - previous_count) DESC, recent_count DESC " +
                "LIMIT 8",
        nativeQuery = true
    )
    List<Object[]> findCategoryGrowthStats();

    @Query(
        value = "SELECT " +
                "  location, " +
                "  category_key, " +
                "  COUNT(*) AS total_tickets, " +
                "  SUM(CASE WHEN status IN ('WAITING_ACCEPT', 'IN_PROGRESS') THEN 1 ELSE 0 END) AS active_tickets, " +
                "  MAX(created_at) AS last_created_at " +
                "FROM repair_order " +
                "WHERE location IS NOT NULL " +
                "  AND TRIM(location) <> '' " +
                "  AND category_key IS NOT NULL " +
                "  AND (is_deleted IS NULL OR is_deleted = false) " +
                "GROUP BY location, category_key " +
                "HAVING total_tickets >= 2 " +
                "ORDER BY total_tickets DESC, active_tickets DESC, last_created_at DESC " +
                "LIMIT 8",
        nativeQuery = true
    )
    List<Object[]> findRepeatedLocationStats();

    @Query(
        value = "SELECT " +
                "  u.user_number, " +
                "  COALESCE(u.name, u.user_number) AS staff_name, " +
                "  COUNT(t.id) AS total_assigned, " +
                "  SUM(CASE WHEN t.status IN ('WAITING_ACCEPT', 'IN_PROGRESS', 'RESOLVED', 'WAITING_FEEDBACK') THEN 1 ELSE 0 END) AS active_tickets, " +
                "  SUM(CASE WHEN t.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN 1 ELSE 0 END) AS completed_tickets " +
                "FROM sys_user u " +
                "LEFT JOIN repair_order t ON t.repairman_id = u.user_number AND (t.is_deleted IS NULL OR t.is_deleted = false) " +
                "WHERE u.role = 'STAFF' AND (u.enabled IS NULL OR u.enabled = true) " +
                "GROUP BY u.user_number, u.name " +
                "ORDER BY active_tickets DESC, total_assigned DESC " +
                "LIMIT 8",
        nativeQuery = true
    )
    List<Object[]> findStaffWorkloadStats();

    @Query(
        value = "SELECT " +
                "  category_key, " +
                "  COUNT(*) AS completed_tickets, " +
                "  AVG(TIMESTAMPDIFF(HOUR, created_at, completed_at)) AS avg_hours " +
                "FROM repair_order " +
                "WHERE category_key IS NOT NULL " +
                "  AND status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') " +
                "  AND created_at IS NOT NULL " +
                "  AND completed_at IS NOT NULL " +
                "  AND (is_deleted IS NULL OR is_deleted = false) " +
                "GROUP BY category_key " +
                "ORDER BY avg_hours DESC " +
                "LIMIT 8",
        nativeQuery = true
    )
    List<Object[]> findCategoryProcessingTimeStats();

    // 使用悲观锁查询工单（用于关键操作）
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RepairTicket rt WHERE rt.ticketId = :id")
    Optional<RepairTicket> findByIdWithLock(@Param("id") Long id);

    // 获取平均处理时间（从创建到完成的平均时长，单位：小时）
    @Query(
        value = "SELECT " +
                "  AVG(TIMESTAMPDIFF(HOUR, created_at, completed_at)) AS avg_hours " +
                "FROM repair_order " +
                "WHERE status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') " +
                "  AND completed_at IS NOT NULL " +
                "  AND created_at IS NOT NULL " +
                "  AND (is_deleted IS NULL OR is_deleted = false)",
        nativeQuery = true
    )
    Double findAverageProcessingTimeHours();

    @Query(
        value = "SELECT COUNT(*) " +
                "FROM repair_order " +
                "WHERE repairman_id = :staffId " +
                "  AND status IN ('IN_PROGRESS', 'RESOLVED', 'WAITING_FEEDBACK') " +
                "  AND (is_deleted IS NULL OR is_deleted = false)",
        nativeQuery = true
    )
    Long countActiveTasksByStaffId(@Param("staffId") String staffId);

    @Query(
        value = "SELECT COUNT(*) " +
                "FROM repair_order " +
                "WHERE repairman_id = :staffId " +
                "  AND status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') " +
                "  AND (is_deleted IS NULL OR is_deleted = false)",
        nativeQuery = true
    )
    Long countCompletedTasksByStaffId(@Param("staffId") String staffId);

    @Query(
        value = "SELECT COUNT(*) " +
                "FROM repair_order " +
                "WHERE repairman_id = :staffId " +
                "  AND category_key = :categoryKey " +
                "  AND status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') " +
                "  AND (is_deleted IS NULL OR is_deleted = false)",
        nativeQuery = true
    )
    Long countCompletedTasksByStaffIdAndCategory(@Param("staffId") String staffId,
                                                 @Param("categoryKey") String categoryKey);

    @Query(
        value = "SELECT COALESCE(AVG(f.rating), 0) " +
                "FROM repair_feedback f " +
                "WHERE f.repairman_id = :staffId",
        nativeQuery = true
    )
    Double findAverageRatingByStaffId(@Param("staffId") String staffId);

    @Query(
        value = "SELECT COALESCE(AVG(TIMESTAMPDIFF(HOUR, created_at, completed_at)), 72) " +
                "FROM repair_order " +
                "WHERE repairman_id = :staffId " +
                "  AND status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') " +
                "  AND completed_at IS NOT NULL " +
                "  AND created_at IS NOT NULL " +
                "  AND (is_deleted IS NULL OR is_deleted = false)",
        nativeQuery = true
    )
    Double findAverageProcessingHoursByStaffId(@Param("staffId") String staffId);

    // 获取已完成工单数量（用于统计）
    @Query(
        value = "SELECT COUNT(*) " +
                "FROM repair_order " +
                "WHERE status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') " +
                "  AND completed_at IS NOT NULL " +
                "  AND (is_deleted IS NULL OR is_deleted = false)",
        nativeQuery = true
    )
    Long countCompletedTickets();

    // ==================== 维修工相关统计查询 ====================

    /**
     * 统计维修工指定状态的工单数量
     */
    @Query(
        value = "SELECT COUNT(*) " +
                "FROM repair_order " +
                "WHERE repairman_id = :staffId " +
                "  AND status = :status " +
                "  AND (is_deleted IS NULL OR is_deleted = false)",
        nativeQuery = true
    )
    Long countByStaffIdAndStatus(@Param("staffId") String staffId, @Param("status") String status);

    /**
     * 统计维修工在指定时间范围内完成的工单数量（数据库无关）
     * 使用左闭右开区间 [start, end)
     *
     * @param staffId 维修工ID
     * @param start 开始时间（包含）
     * @param end 结束时间（不包含）
     * @return 完成的工单数量
     */
    @Query("SELECT COUNT(rt) FROM RepairTicket rt " +
           "WHERE rt.staff.userId = :staffId " +
           "  AND rt.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') " +
           "  AND rt.completedAt >= :start " +
           "  AND rt.completedAt < :end " +
           "  AND rt.completedAt IS NOT NULL " +
           "  AND (rt.deleted IS NULL OR rt.deleted = false)")
    Long countCompletedByStaffIdAndCompletedAtBetween(
            @Param("staffId") String staffId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * 统计维修工今日完成的工单数量
     * @deprecated 使用 {@link #countCompletedByStaffIdAndCompletedAtBetween} 替代
     */
    @Deprecated
    @Query(
        value = "SELECT COUNT(*) " +
                "FROM repair_order " +
                "WHERE repairman_id = :staffId " +
                "  AND status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') " +
                "  AND DATE(completed_at) = CURDATE() " +
                "  AND (is_deleted IS NULL OR is_deleted = false)",
        nativeQuery = true
    )
    Long countTodayCompletedByStaffId(@Param("staffId") String staffId);

    /**
     * 统计维修工本周完成的工单数量
     */
    @Query(
        value = "SELECT COUNT(*) " +
                "FROM repair_order " +
                "WHERE repairman_id = :staffId " +
                "  AND status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') " +
                "  AND YEARWEEK(completed_at, 1) = YEARWEEK(CURDATE(), 1) " +
                "  AND (is_deleted IS NULL OR is_deleted = false)",
        nativeQuery = true
    )
    Long countWeekCompletedByStaffId(@Param("staffId") String staffId);

    /**
     * 统计维修工本月完成的工单数量
     */
    @Query(
        value = "SELECT COUNT(*) " +
                "FROM repair_order " +
                "WHERE repairman_id = :staffId " +
                "  AND status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') " +
                "  AND DATE_FORMAT(completed_at, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m') " +
                "  AND (is_deleted IS NULL OR is_deleted = false)",
        nativeQuery = true
    )
    Long countMonthCompletedByStaffId(@Param("staffId") String staffId);

    /**
     * 获取维修工的工单优先级分布
     */
    @Query(
        value = "SELECT priority, COUNT(*) as count " +
                "FROM repair_order " +
                "WHERE repairman_id = :staffId " +
                "  AND (is_deleted IS NULL OR is_deleted = false) " +
                "GROUP BY priority",
        nativeQuery = true
    )
    List<Object[]> findPriorityDistributionByStaffId(@Param("staffId") String staffId);

    /**
     * 获取维修工的工单分类分布
     */
    @Query(
        value = "SELECT category_key, COUNT(*) as count " +
                "FROM repair_order " +
                "WHERE repairman_id = :staffId " +
                "  AND (is_deleted IS NULL OR is_deleted = false) " +
                "GROUP BY category_key",
        nativeQuery = true
    )
    List<Object[]> findCategoryDistributionByStaffId(@Param("staffId") String staffId);

    /**
     * 获取维修工收到的评价统计
     */
    @Query(
        value = "SELECT COUNT(*) as total_count, COALESCE(AVG(rating), 0) as avg_rating " +
                "FROM repair_feedback " +
                "WHERE repairman_id = :staffId",
        nativeQuery = true
    )
    Object[] findRatingStatsByStaffId(@Param("staffId") String staffId);

    /**
     * 分页查询维修工的工单（支持状态筛选）
     */
    @Query(
        value = "SELECT rt FROM RepairTicket rt WHERE rt.staff.userId = :staffId " +
                "AND (:status IS NULL OR rt.status = :status) " +
                "AND (rt.deleted IS NULL OR rt.deleted = false) " +
                "ORDER BY rt.createdAt DESC",
        countQuery = "SELECT COUNT(rt) FROM RepairTicket rt WHERE rt.staff.userId = :staffId " +
                "AND (:status IS NULL OR rt.status = :status) " +
                "AND (rt.deleted IS NULL OR rt.deleted = false)"
    )
    org.springframework.data.domain.Page<RepairTicket> findByStaffIdWithFilter(
            @Param("staffId") String staffId,
            @Param("status") TicketStatus status,
            org.springframework.data.domain.Pageable pageable
    );
}