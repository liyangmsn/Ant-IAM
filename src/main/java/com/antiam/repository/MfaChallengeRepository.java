package com.antiam.repository;

import com.antiam.domain.MfaChallenge;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, UUID> {
    Optional<MfaChallenge> findByChallengeId(String challengeId);

    boolean existsByFactorId(UUID factorId);
}
