package com.antiam.repository;

import com.antiam.domain.CredentialType;
import com.antiam.domain.UserCredentialHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialHistoryRepository extends JpaRepository<UserCredentialHistory, UUID> {
    List<UserCredentialHistory> findByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, CredentialType type);
}
