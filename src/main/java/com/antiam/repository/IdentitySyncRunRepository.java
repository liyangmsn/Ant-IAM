package com.antiam.repository;

import com.antiam.domain.IdentitySyncRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentitySyncRunRepository extends JpaRepository<IdentitySyncRun, UUID> {
    List<IdentitySyncRun> findTop50BySyncJobIdOrderByCreatedAtDesc(UUID syncJobId);

    List<IdentitySyncRun> findTop100ByOrderByCreatedAtDesc();
}
