package com.antiam.repository;

import com.antiam.domain.OAuthConsent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OAuthConsentRepository extends JpaRepository<OAuthConsent, UUID> {
    Optional<OAuthConsent> findByClientIdAndUserId(String clientId, UUID userId);

    @Query("select c from OAuthConsent c join fetch c.user where c.id = :consentId")
    Optional<OAuthConsent> findByIdWithUser(@Param("consentId") UUID consentId);

    void deleteByApplicationId(UUID applicationId);

    List<OAuthConsent> findByUserId(UUID userId);
}
