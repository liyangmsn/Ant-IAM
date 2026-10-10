package com.antiam.repository;

import com.antiam.domain.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    long countByIdentitySourceId(UUID identitySourceId);

    java.util.List<Organization> findByIdentitySourceIdOrderByCreatedAtAsc(UUID identitySourceId);

    Optional<Organization> findByIdAndIdentitySourceId(UUID id, UUID identitySourceId);

    Optional<Organization> findByIdentitySourceIdAndExternalId(UUID identitySourceId, String externalId);

    boolean existsByIdentitySourceIdAndExternalId(UUID identitySourceId, String externalId);

    boolean existsByCode(String code);

    java.util.List<Organization> findTop20ByCodeContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCodeAsc(String code, String name);

    Optional<Organization> findByCode(String code);

    boolean existsByParentId(UUID parentId);
}
