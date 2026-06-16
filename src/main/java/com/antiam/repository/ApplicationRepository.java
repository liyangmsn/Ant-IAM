package com.antiam.repository;

import com.antiam.domain.Application;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    Optional<Application> findByCode(String code);

    java.util.List<Application> findByTenantId(UUID tenantId);

    java.util.List<Application> findByGroupId(UUID groupId);

    long countByGroupId(UUID groupId);
}
