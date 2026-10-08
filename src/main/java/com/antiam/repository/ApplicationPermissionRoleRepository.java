package com.antiam.repository;

import com.antiam.domain.ApplicationPermissionRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationPermissionRoleRepository extends JpaRepository<ApplicationPermissionRole, UUID> {
    List<ApplicationPermissionRole> findByApplicationIdOrderByCodeAsc(UUID applicationId);

    Optional<ApplicationPermissionRole> findByIdAndApplicationId(UUID id, UUID applicationId);

    boolean existsByApplicationIdAndCode(UUID applicationId, String code);

    List<ApplicationPermissionRole> findByPermissionsId(UUID permissionId);
}
