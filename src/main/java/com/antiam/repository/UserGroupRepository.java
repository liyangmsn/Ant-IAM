package com.antiam.repository;

import com.antiam.domain.UserGroup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGroupRepository extends JpaRepository<UserGroup, UUID> {
    Optional<UserGroup> findByCode(String code);

    java.util.List<UserGroup> findByRolesId(UUID roleId);
}
