package com.qiyun.opsservice.repository;

import com.qiyun.opsservice.domain.entity.UserReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserReferenceRepository extends JpaRepository<UserReference, String> {

    Optional<UserReference> findByUserIdAndIsActiveTrue(String userId);
}