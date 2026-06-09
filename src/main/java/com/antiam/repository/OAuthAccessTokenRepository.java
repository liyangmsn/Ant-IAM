package com.antiam.repository;

import com.antiam.domain.OAuthAccessToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccessTokenRepository extends JpaRepository<OAuthAccessToken, UUID> {
    Optional<OAuthAccessToken> findByTokenHash(String tokenHash);

    List<OAuthAccessToken> findByUserId(UUID userId);
}
