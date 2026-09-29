package com.antiam.repository;

import com.antiam.domain.IdentitySyncRun;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentitySyncRunRepository extends JpaRepository<IdentitySyncRun, UUID> {
    List<IdentitySyncRun> findTop50BySyncJobIdOrderByCreatedAtDesc(UUID syncJobId);

    List<IdentitySyncRun> findTop100ByOrderByCreatedAtDesc();

    @Query("select r.status, count(r) from IdentitySyncRun r where r.createdAt >= :from group by r.status")
    List<Object[]> countByStatusSince(@Param("from") java.time.Instant from);

    java.util.Optional<IdentitySyncRun> findTopBySyncJobIdOrderByStartedAtDesc(UUID syncJobId);
}
