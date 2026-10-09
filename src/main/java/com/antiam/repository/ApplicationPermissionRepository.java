package com.antiam.repository;

import com.antiam.domain.ApplicationPermission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationPermissionRepository extends JpaRepository<ApplicationPermission, UUID> {
    List<ApplicationPermission> findByApplicationIdOrderByCodeAsc(UUID applicationId);

    Optional<ApplicationPermission> findByIdAndApplicationId(UUID id, UUID applicationId);

    boolean existsByApplicationIdAndCode(UUID applicationId, String code);

    Optional<ApplicationPermission> findByApplicationIdAndCode(UUID applicationId, String code);
}
