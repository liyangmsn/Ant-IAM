package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.domain.SettingValueType;
import com.antiam.domain.SystemSetting;
import com.antiam.repository.SystemSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeoIpServiceTest {

    private final SystemSettingRepository settings = mock(SystemSettingRepository.class);
    private final GeoIpService service = new GeoIpService(settings, "");

    @Test
    void resolvesLoopbackAndPrivateRangesInSystemMode() {
        when(settings.findBySettingKey("geoip.provider"))
            .thenReturn(Optional.of(setting("system")));

        assertThat(service.location("127.0.0.1")).isEqualTo("本机");
        assertThat(service.location("192.168.1.10")).isEqualTo("内网");
        assertThat(service.location("172.20.0.4")).isEqualTo("内网");
        assertThat(service.location("8.8.8.8")).isEqualTo("公网");
    }

    @Test
    void returnsNullForBlankAddress() {
        assertThat(service.location(null)).isNull();
        assertThat(service.location("   ")).isNull();
    }

    @Test
    void rejectsMaxmindModeWithoutDatabasePath() {
        when(settings.findBySettingKey("geoip.provider"))
            .thenReturn(Optional.of(setting("maxmind")));

        assertThatThrownBy(() -> service.location("8.8.8.8"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("MaxMind database path is not configured");
    }

    @Test
    void rejectsMaxmindModeWhenDatabaseFileIsMissing() {
        GeoIpService missingDatabase = new GeoIpService(settings, "/tmp/does-not-exist-" + System.nanoTime() + ".mmdb");
        when(settings.findBySettingKey("geoip.provider"))
            .thenReturn(Optional.of(setting("maxmind")));

        assertThatThrownBy(() -> missingDatabase.location("8.8.8.8"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to open MaxMind database");
    }

    private SystemSetting setting(String value) {
        return new SystemSetting("geoip.provider", "geo-ip", SettingValueType.STRING, value, "geoip.provider", false);
    }
}
