package com.antiam.repository;

import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.AuthenticationEventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationEventRepository extends JpaRepository<AuthenticationEvent, UUID> {
    List<AuthenticationEvent> findTop100ByOrderByCreatedAtDesc();

    List<AuthenticationEvent> findTop100ByTypeOrderByCreatedAtDesc(AuthenticationEventType type);

    long countByUserIdAndTypeAndCreatedAtAfter(UUID userId, AuthenticationEventType type, Instant createdAt);
}
