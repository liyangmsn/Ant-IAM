package com.antiam.repository;

import com.antiam.domain.OAuthRefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthRefreshTokenRepository extends JpaRepository<OAuthRefreshToken, UUID> {
    Optional<OAuthRefreshToken> findByTokenHash(String tokenHash);

    void deleteByApplicationId(UUID applicationId);

    List<OAuthRefreshToken> findByUserId(UUID userId);
}
