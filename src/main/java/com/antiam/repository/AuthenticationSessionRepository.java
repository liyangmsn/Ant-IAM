package com.antiam.repository;

import com.antiam.domain.AuthenticationSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationSessionRepository extends JpaRepository<AuthenticationSession, UUID> {
    List<AuthenticationSession> findByActive(boolean active);

    List<AuthenticationSession> findByUserIdAndActive(UUID userId, boolean active);

    List<AuthenticationSession> findByApplicationIdAndActive(UUID applicationId, boolean active);

    List<AuthenticationSession> findByUserIdAndApplicationIdAndActive(UUID userId, UUID applicationId, boolean active);
}
