package com.antiam.repository;

import com.antiam.domain.IdentitySource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentitySourceRepository extends JpaRepository<IdentitySource, UUID> {
    Optional<IdentitySource> findByCode(String code);

    java.util.List<IdentitySource> findByTenantId(UUID tenantId);
}
