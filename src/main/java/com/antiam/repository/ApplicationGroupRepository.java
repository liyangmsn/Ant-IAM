package com.antiam.repository;

import com.antiam.domain.ApplicationGroup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationGroupRepository extends JpaRepository<ApplicationGroup, UUID> {
    Optional<ApplicationGroup> findByCode(String code);
}
