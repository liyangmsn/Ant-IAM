package com.antiam.repository;

import com.antiam.domain.OAuthConsent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthConsentRepository extends JpaRepository<OAuthConsent, UUID> {
    Optional<OAuthConsent> findByClientIdAndUserId(String clientId, UUID userId);

    void deleteByApplicationId(UUID applicationId);

    List<OAuthConsent> findByUserId(UUID userId);
}
