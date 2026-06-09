package com.antiam.repository;

import com.antiam.domain.RiskAssessment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {
    List<RiskAssessment> findTop100ByOrderByCreatedAtDesc();

    java.util.Optional<RiskAssessment> findFirstByUserIdAndDeviceFingerprintIsNotNullOrderByCreatedAtDesc(UUID userId);
}
