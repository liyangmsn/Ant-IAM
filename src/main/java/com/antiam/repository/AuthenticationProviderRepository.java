package com.antiam.repository;

import com.antiam.domain.AuthenticationProvider;
import com.antiam.domain.AuthenticationProviderType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationProviderRepository extends JpaRepository<AuthenticationProvider, UUID> {
    Optional<AuthenticationProvider> findByProviderKey(String providerKey);

    List<AuthenticationProvider> findByTypeOrderByCreatedAtAsc(AuthenticationProviderType type);
}
