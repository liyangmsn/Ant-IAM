package com.antiam.repository;

import com.antiam.domain.IdentitySyncJob;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentitySyncJobRepository extends JpaRepository<IdentitySyncJob, UUID> {
    List<IdentitySyncJob> findByIdentitySourceId(UUID identitySourceId);
}
