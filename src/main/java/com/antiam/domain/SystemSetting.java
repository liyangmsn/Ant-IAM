package com.antiam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "system_settings")
public class SystemSetting extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String settingKey;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    private SettingValueType valueType;

    @Column(columnDefinition = "text")
    private String settingValue;

    private String description;
    private boolean sensitive;

    public SystemSetting(
        String settingKey,
        String category,
        SettingValueType valueType,
        String settingValue,
        String description,
        boolean sensitive
    ) {
        this.settingKey = settingKey;
        this.category = category;
        this.valueType = valueType;
        this.settingValue = settingValue;
        this.description = description;
        this.sensitive = sensitive;
    }

    public void update(SettingValueType valueType, String settingValue, String description, boolean sensitive) {
        this.valueType = valueType;
        this.settingValue = settingValue;
        this.description = description;
        this.sensitive = sensitive;
    }
}
