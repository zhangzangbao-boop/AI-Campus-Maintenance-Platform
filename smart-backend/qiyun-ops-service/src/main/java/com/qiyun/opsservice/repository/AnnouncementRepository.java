package com.qiyun.opsservice.repository;

import com.qiyun.opsservice.domain.entity.Announcement;
import com.qiyun.opsservice.domain.enums.AnnouncementStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findAllByOrderByPinnedDescPublishTimeDescUpdatedAtDesc();

    @Query("""
        select a
        from Announcement a
        where a.status = :status
          and (a.publishTime is null or a.publishTime <= :now)
          and (a.expireTime is null or a.expireTime > :now)
        order by a.pinned desc, a.publishTime desc, a.updatedAt desc
        """)
    List<Announcement> findVisible(@Param("status") AnnouncementStatus status, @Param("now") LocalDateTime now);
}
