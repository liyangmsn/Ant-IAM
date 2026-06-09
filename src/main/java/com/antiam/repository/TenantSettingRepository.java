package com.antiam.repository;

import com.antiam.domain.TenantSetting;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingRepository extends JpaRepository<TenantSetting, UUID> {
    Optional<TenantSetting> findByTenantIdAndSettingKey(UUID tenantId, String settingKey);

    List<TenantSetting> findByTenantIdOrderBySettingKeyAsc(UUID tenantId);

    List<TenantSetting> findByTenantIdAndCategoryOrderBySettingKeyAsc(UUID tenantId, String category);
}
