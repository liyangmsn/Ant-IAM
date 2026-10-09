package com.antiam.repository;

import com.antiam.domain.ApplicationPermissionRoleMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationPermissionRoleMemberRepository extends JpaRepository<ApplicationPermissionRoleMember, UUID> {
    List<ApplicationPermissionRoleMember> findByRoleIdOrderByCreatedAtAsc(UUID roleId);

    Optional<ApplicationPermissionRoleMember> findByIdAndRoleId(UUID id, UUID roleId);

    long countByRoleId(UUID roleId);

    boolean existsByRoleIdAndUserId(UUID roleId, UUID userId);

    boolean existsByRoleIdAndGroupId(UUID roleId, UUID groupId);

    boolean existsByRoleIdAndOrganizationId(UUID roleId, UUID organizationId);

    @Query("""
        select m from ApplicationPermissionRoleMember m
        join fetch m.role r
        left join fetch r.permissions
        where r.application.id = :applicationId
        """)
    List<ApplicationPermissionRoleMember> findByApplicationIdWithPermissions(@Param("applicationId") UUID applicationId);

    @Query("""
        select m from ApplicationPermissionRoleMember m
        join fetch m.role r
        join fetch r.application
        where r.builtIn = true
        """)
    List<ApplicationPermissionRoleMember> findBuiltInRoleMembers();
}
