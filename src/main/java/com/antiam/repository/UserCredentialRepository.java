package com.antiam.repository;

import com.antiam.domain.CredentialType;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {
    Optional<UserCredential> findByUserAndType(UserAccount user, CredentialType type);
}
