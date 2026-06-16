package com.antiam.repository;

import com.antiam.domain.OAuthAuthorizationCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAuthorizationCodeRepository extends JpaRepository<OAuthAuthorizationCode, UUID> {
    Optional<OAuthAuthorizationCode> findByCodeHash(String codeHash);

    void deleteByApplicationId(UUID applicationId);
}
