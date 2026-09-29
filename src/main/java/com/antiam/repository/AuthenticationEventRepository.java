package com.antiam.repository;

import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.AuthenticationEventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthenticationEventRepository extends JpaRepository<AuthenticationEvent, UUID> {
    List<AuthenticationEvent> findTop100ByOrderByCreatedAtDesc();

    List<AuthenticationEvent> findTop100ByTypeOrderByCreatedAtDesc(AuthenticationEventType type);

    List<AuthenticationEvent> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(Instant createdAt);

    void deleteByApplicationId(UUID applicationId);

    long countByCreatedAtGreaterThanEqual(Instant createdAt);

    @Query("select e.type, count(e) from AuthenticationEvent e where e.createdAt >= :from group by e.type")
    List<Object[]> countByTypeSince(@Param("from") Instant from);

    long countByUserIdAndTypeAndCreatedAtAfter(UUID userId, AuthenticationEventType type, Instant createdAt);
}
