package com.antiam.repository;

import com.antiam.domain.ApplicationAccessRequest;
import com.antiam.domain.ApplicationAccessRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationAccessRequestRepository extends JpaRepository<ApplicationAccessRequest, UUID> {
    List<ApplicationAccessRequest> findByStatusOrderByCreatedAtDesc(ApplicationAccessRequestStatus status);

    List<ApplicationAccessRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ApplicationAccessRequest> findByApplicationIdAndUserIdAndStatus(
        UUID applicationId,
        UUID userId,
        ApplicationAccessRequestStatus status);
}
