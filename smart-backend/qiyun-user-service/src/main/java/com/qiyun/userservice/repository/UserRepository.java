package com.qiyun.userservice.repository;

import com.qiyun.userservice.domain.entity.User;
import com.qiyun.userservice.domain.enums.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUserId(String userId);

    boolean existsByUserId(String userId);

    @Query("SELECT u FROM User u WHERE u.userId = ?1 AND u.isActive = true")
    Optional<User> findActiveByUserId(String userId);

    @Query("SELECT u FROM User u WHERE u.nickname = ?1 AND u.isActive = true")
    Optional<User> findActiveByNickname(String nickname);

    List<User> findByRoleAndIsActiveTrue(UserRole role);

    List<User> findAllByOrderByCreatedAtDesc();
}