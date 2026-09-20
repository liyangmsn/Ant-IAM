package com.antiam.repository;

import com.antiam.domain.JwtSigningKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JwtSigningKeyRepository extends JpaRepository<JwtSigningKey, UUID> {
    Optional<JwtSigningKey> findFirstByRetiredAtIsNullOrderByActivatedAtDesc();

    Optional<JwtSigningKey> findByKeyId(String keyId);

    java.util.List<JwtSigningKey> findByRetiredAtIsNullOrderByActivatedAtDesc();

    java.util.List<JwtSigningKey> findAllByOrderByActivatedAtDesc();
}
