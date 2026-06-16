package com.antiam.repository;

import com.antiam.domain.ApplicationSsoConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationSsoConfigRepository extends JpaRepository<ApplicationSsoConfig, UUID> {
    Optional<ApplicationSsoConfig> findByApplicationId(UUID applicationId);

    void deleteByApplicationId(UUID applicationId);

    Optional<ApplicationSsoConfig> findByClientId(String clientId);

    Optional<ApplicationSsoConfig> findBySamlEntityId(String samlEntityId);

    Optional<ApplicationSsoConfig> findByCasServiceUrl(String casServiceUrl);
}
