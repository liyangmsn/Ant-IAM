package com.antiam.repository;

import com.antiam.domain.AuthenticationSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationSessionRepository extends JpaRepository<AuthenticationSession, UUID> {
    List<AuthenticationSession> findByActive(boolean active);

    @EntityGraph(attributePaths = "application")
    List<AuthenticationSession> findByCreatedAtGreaterThanEqual(Instant createdAt);

    @EntityGraph(attributePaths = "user")
    Optional<AuthenticationSession> findBySessionIndexAndActive(String sessionIndex, boolean active);

    List<AuthenticationSession> findByUserIdAndActive(UUID userId, boolean active);

    List<AuthenticationSession> findByApplicationIdAndActive(UUID applicationId, boolean active);

    void deleteByApplicationId(UUID applicationId);

    List<AuthenticationSession> findByUserIdAndApplicationIdAndActive(UUID userId, UUID applicationId, boolean active);
}
