package com.antiam.repository;

import com.antiam.domain.ApplicationAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationAssignmentRepository extends JpaRepository<ApplicationAssignment, UUID> {
    List<ApplicationAssignment> findByApplicationId(UUID applicationId);

    void deleteByApplicationId(UUID applicationId);

    List<ApplicationAssignment> findByUserId(UUID userId);

    List<ApplicationAssignment> findByGroupId(UUID groupId);

    List<ApplicationAssignment> findByOrganizationId(UUID organizationId);

    Optional<ApplicationAssignment> findByApplicationIdAndUserId(UUID applicationId, UUID userId);

    Optional<ApplicationAssignment> findByApplicationIdAndGroupId(UUID applicationId, UUID groupId);

    Optional<ApplicationAssignment> findByApplicationIdAndOrganizationId(UUID applicationId, UUID organizationId);
}
