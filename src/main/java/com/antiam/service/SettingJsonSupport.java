package com.antiam.service;

import com.antiam.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SettingJsonSupport {

    private final SystemSettingRepository settings;
    private final ObjectMapper objectMapper;

    JsonNode json(String settingKey) {
        return settings.findBySettingKey(settingKey)
            .map(setting -> parse(settingKey, setting.getSettingValue()))
            .orElse(objectMapper.createObjectNode());
    }

    String string(String settingKey, String defaultValue) {
        return settings.findBySettingKey(settingKey)
            .map(setting -> setting.getSettingValue() == null || setting.getSettingValue().isBlank()
                ? defaultValue
                : setting.getSettingValue())
            .orElse(defaultValue);
    }

    private JsonNode parse(String settingKey, String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("System setting is not valid JSON: " + settingKey, ex);
        }
    }
}
