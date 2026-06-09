package com.antiam.repository;

import com.antiam.domain.RiskRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskRuleRepository extends JpaRepository<RiskRule, UUID> {
    Optional<RiskRule> findByCode(String code);

    List<RiskRule> findByEnabledTrueOrderByCreatedAtAsc();
}
