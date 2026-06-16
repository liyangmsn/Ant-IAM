package com.antiam.repository;

import com.antiam.domain.SamlAssertion;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SamlAssertionRepository extends JpaRepository<SamlAssertion, UUID> {
    void deleteByApplicationId(UUID applicationId);
}
