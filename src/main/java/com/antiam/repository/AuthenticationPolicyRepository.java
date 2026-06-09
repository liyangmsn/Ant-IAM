package com.antiam.repository;

import com.antiam.domain.AuthenticationPolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationPolicyRepository extends JpaRepository<AuthenticationPolicy, UUID> {
    Optional<AuthenticationPolicy> findByCode(String code);

    List<AuthenticationPolicy> findByEnabledTrueOrderByPriorityAsc();
}
