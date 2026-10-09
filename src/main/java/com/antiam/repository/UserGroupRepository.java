package com.antiam.repository;

import com.antiam.domain.UserGroup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserGroupRepository extends JpaRepository<UserGroup, UUID> {
    java.util.List<UserGroup> findTop20ByCodeContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCodeAsc(String code, String name);

    Optional<UserGroup> findByCode(String code);

    java.util.List<UserGroup> findByRolesId(UUID roleId);

    @Query("""
        select case when count(g) > 0 then true else false end
        from UserGroup g
        join g.roles r
        left join r.permissions p
        where g.id = :groupId
          and (r.code = :adminRoleCode or p.code like :permissionPrefix)
        """)
    boolean holdsConsoleAccess(
        @Param("groupId") UUID groupId,
        @Param("adminRoleCode") String adminRoleCode,
        @Param("permissionPrefix") String permissionPrefix
    );
}
