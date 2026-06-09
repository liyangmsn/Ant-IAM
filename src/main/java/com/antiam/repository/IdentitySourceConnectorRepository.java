package com.antiam.repository;

import com.antiam.domain.IdentitySourceConnector;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentitySourceConnectorRepository extends JpaRepository<IdentitySourceConnector, UUID> {
    Optional<IdentitySourceConnector> findByIdentitySourceId(UUID identitySourceId);
}
