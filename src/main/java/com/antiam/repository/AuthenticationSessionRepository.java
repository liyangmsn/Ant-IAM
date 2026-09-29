package com.antiam.repository;

import com.antiam.domain.AuthenticationSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthenticationSessionRepository extends JpaRepository<AuthenticationSession, UUID> {
    List<AuthenticationSession> findByActive(boolean active);

    long countByActiveTrueAndExpiresAtAfter(Instant now);

    boolean existsBySessionIndex(String sessionIndex);

    @EntityGraph(attributePaths = "application")
    List<AuthenticationSession> findByCreatedAtGreaterThanEqual(Instant createdAt);

    @EntityGraph(attributePaths = {"user", "user.tenant"})
    Optional<AuthenticationSession> findBySessionIndexAndActive(String sessionIndex, boolean active);

    @Query("select s from AuthenticationSession s join fetch s.user where s.id = :sessionId")
    Optional<AuthenticationSession> findByIdWithUser(@Param("sessionId") UUID sessionId);

    List<AuthenticationSession> findByUserIdAndActive(UUID userId, boolean active);

    /**
     * 批量结束用户的活跃会话，不把会话实体载入持久化上下文，删除用户时避免会话引用已删除的用户实体。
     */
    @Modifying
    @Query("update AuthenticationSession s set s.active = false, s.endedAt = :endedAt where s.user.id = :userId and s.active = true")
    int endActiveByUserId(@Param("userId") UUID userId, @Param("endedAt") Instant endedAt);

    List<AuthenticationSession> findByUserTenantIdAndActive(UUID tenantId, boolean active);

    List<AuthenticationSession> findByApplicationIdAndActive(UUID applicationId, boolean active);

    void deleteByApplicationId(UUID applicationId);

    List<AuthenticationSession> findByUserIdAndApplicationIdAndActive(UUID userId, UUID applicationId, boolean active);
}
