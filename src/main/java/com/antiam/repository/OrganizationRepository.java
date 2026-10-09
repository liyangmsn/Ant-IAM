package com.antiam.repository;

import com.antiam.domain.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    java.util.List<Organization> findTop20ByCodeContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCodeAsc(String code, String name);

    Optional<Organization> findByCode(String code);

    boolean existsByParentId(UUID parentId);
}
