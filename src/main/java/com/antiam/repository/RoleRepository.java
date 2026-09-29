package com.antiam.repository;

import com.antiam.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);

    List<Role> findByPermissionsId(UUID permissionId);

    @Query("""
        select case when count(r) > 0 then true else false end
        from Role r
        left join r.permissions p
        where r.id = :roleId
          and (r.code = :adminRoleCode or p.code like :permissionPrefix)
        """)
    boolean holdsConsoleAccess(
        @Param("roleId") UUID roleId,
        @Param("adminRoleCode") String adminRoleCode,
        @Param("permissionPrefix") String permissionPrefix
    );
}
