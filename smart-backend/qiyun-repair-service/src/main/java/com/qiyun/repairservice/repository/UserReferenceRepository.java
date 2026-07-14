package com.qiyun.repairservice.repository;

import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 用户引用仓库（只读）
 * 用于查询用户基本信息，不提供写操作
 */
@Repository
public interface UserReferenceRepository extends JpaRepository<UserReference, String> {

    /**
     * 根据用户ID查询
     */
    Optional<UserReference> findByUserId(String userId);

    /**
     * 查询活跃用户
     */
    Optional<UserReference> findByUserIdAndIsActiveTrue(String userId);

    /**
     * 按角色查询活跃用户
     */
    List<UserReference> findByRoleAndIsActiveTrue(UserRole role);

    /**
     * 查询所有活跃用户
     */
    List<UserReference> findByIsActiveTrue();
}