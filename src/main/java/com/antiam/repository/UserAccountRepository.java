package com.antiam.repository;

import com.antiam.domain.AccountStatus;
import com.antiam.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByUsername(String username);

    long countByStatus(AccountStatus status);

    java.util.List<UserAccount> findByTenantId(UUID tenantId);

    java.util.List<UserAccount> findByOrganizationId(UUID organizationId);

    boolean existsByOrganizationId(UUID organizationId);

    java.util.List<UserAccount> findByGroupsId(UUID groupId);

    java.util.List<UserAccount> findByRolesId(UUID roleId);
}
