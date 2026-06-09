package com.antiam.repository;

import com.antiam.domain.MfaFactor;
import com.antiam.domain.MfaFactorType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaFactorRepository extends JpaRepository<MfaFactor, UUID> {
    List<MfaFactor> findByUserId(UUID userId);

    Optional<MfaFactor> findByUserIdAndType(UUID userId, MfaFactorType type);

    boolean existsByUserIdAndEnabledTrue(UUID userId);
}
