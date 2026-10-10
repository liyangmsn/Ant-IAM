package com.antiam.repository;

import com.antiam.domain.AccountStatus;
import com.antiam.domain.UserAccount;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    long countByIdentitySourceId(UUID identitySourceId);

    java.util.List<UserAccount> findByIdentitySourceIdOrderByCreatedAtAsc(UUID identitySourceId);

    Optional<UserAccount> findByIdAndIdentitySourceId(UUID id, UUID identitySourceId);

    boolean existsByIdentitySourceIdAndExternalId(UUID identitySourceId, String externalId);

    boolean existsByIdentitySourceIdAndExternalIdAndIdNot(UUID identitySourceId, String externalId, UUID id);

    Optional<UserAccount> findByUsername(String username);

    /** 委派管理员搜索可授予对象：只返回在职用户，pattern 为小写的 like 模式。 */
    @Query("""
        select u from UserAccount u
        where u.status = com.antiam.domain.AccountStatus.ACTIVE
          and (lower(u.username) like :pattern or lower(u.displayName) like :pattern
               or lower(coalesce(u.email, '')) like :pattern or coalesce(u.mobile, '') like :pattern)
        order by u.username
        """)
    java.util.List<UserAccount> searchActive(@Param("pattern") String pattern, org.springframework.data.domain.Pageable pageable);

    /** 同上，限定在应用所属租户内。 */
    @Query("""
        select u from UserAccount u
        where u.status = com.antiam.domain.AccountStatus.ACTIVE
          and u.tenant.id = :tenantId
          and (lower(u.username) like :pattern or lower(u.displayName) like :pattern
               or lower(coalesce(u.email, '')) like :pattern or coalesce(u.mobile, '') like :pattern)
        order by u.username
        """)
    java.util.List<UserAccount> searchActiveInTenant(
        @Param("tenantId") UUID tenantId,
        @Param("pattern") String pattern,
        org.springframework.data.domain.Pageable pageable);

    Optional<UserAccount> findByMobile(String mobile);

    boolean existsByUsername(String username);

    java.util.List<UserAccount> findAllByMobile(String mobile);

    boolean existsByMobileAndIdNot(String mobile, UUID id);

    boolean existsByMobile(String mobile);

    @Query("""
        select distinct r.code from UserAccount u join u.roles r where u.username = :username
        union
        select distinct r.code from UserAccount u join u.groups g join g.roles r where u.username = :username
        """)
    Set<String> findEffectiveRoleCodesByUsername(@Param("username") String username);

    @Query("""
        select distinct p.code from UserAccount u join u.roles r join r.permissions p where u.username = :username
        union
        select distinct p.code from UserAccount u join u.groups g join g.roles r join r.permissions p where u.username = :username
        """)
    Set<String> findEffectivePermissionCodesByUsername(@Param("username") String username);

    @Query("""
        select case when count(u) > 0 then true else false end
        from UserAccount u
        left join u.roles directRole
        left join directRole.permissions directPermission
        left join u.groups userGroup
        left join userGroup.roles groupRole
        left join groupRole.permissions groupPermission
        where u.id = :userId
          and (directRole.code = :adminRoleCode
            or groupRole.code = :adminRoleCode
            or directPermission.code like :permissionPrefix
            or groupPermission.code like :permissionPrefix)
        """)
    boolean holdsConsoleAccess(
        @Param("userId") UUID userId,
        @Param("adminRoleCode") String adminRoleCode,
        @Param("permissionPrefix") String permissionPrefix
    );

    long countByStatus(AccountStatus status);

    java.util.List<UserAccount> findByTenantId(UUID tenantId);

    java.util.List<UserAccount> findByIdentitySourceId(UUID identitySourceId);

    java.util.List<UserAccount> findByOrganizationId(UUID organizationId);

    boolean existsByOrganizationId(UUID organizationId);

    boolean existsByTenantId(UUID tenantId);

    java.util.List<UserAccount> findByGroupsId(UUID groupId);

    java.util.List<UserAccount> findByRolesId(UUID roleId);
}
