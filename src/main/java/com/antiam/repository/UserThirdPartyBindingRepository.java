package com.antiam.repository;

import com.antiam.domain.UserThirdPartyBinding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserThirdPartyBindingRepository extends JpaRepository<UserThirdPartyBinding, UUID> {
    List<UserThirdPartyBinding> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<UserThirdPartyBinding> findByUserIdAndProviderKey(UUID userId, String providerKey);

    Optional<UserThirdPartyBinding> findByProviderKeyAndSubject(String providerKey, String subject);
}
