package com.antiam.repository;

import com.antiam.domain.RiskAssessment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {
    List<RiskAssessment> findTop100ByOrderByCreatedAtDesc();

    @Query("select r.riskLevel, count(r) from RiskAssessment r where r.createdAt >= :from group by r.riskLevel")
    List<Object[]> countByRiskLevelSince(@Param("from") java.time.Instant from);

    java.util.Optional<RiskAssessment> findFirstByUserIdAndDeviceFingerprintIsNotNullOrderByCreatedAtDesc(UUID userId);
}
