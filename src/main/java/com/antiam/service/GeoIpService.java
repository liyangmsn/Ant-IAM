package com.antiam.service;

import com.antiam.domain.SystemSetting;
import com.antiam.repository.SystemSettingRepository;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeoIpService {

    private static final String SETTING_KEY = "geoip.provider";
    private static final String MAXMIND_PROVIDER = "maxmind";
    private static final String CHINESE_LOCALE = "zh-CN";

    private final SystemSettingRepository settings;
    private final String databasePath;
    private volatile DatabaseReader reader;

    public GeoIpService(
        SystemSettingRepository settings,
        @Value("${iam.geoip.database-path:}") String databasePath
    ) {
        this.settings = settings;
        this.databasePath = databasePath;
    }

    /**
     * 解析 IP 地理位置；MaxMind 模式返回国家与城市，系统默认模式返回地址段分类。
     */
    public String location(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        String ip = ipAddress.trim();
        return usesMaxmind() ? maxmindLocation(ip) : systemLocation(ip);
    }

    private boolean usesMaxmind() {
        return settings.findBySettingKey(SETTING_KEY)
            .map(SystemSetting::getSettingValue)
            .map(value -> MAXMIND_PROVIDER.equalsIgnoreCase(value.trim()))
            .orElse(false);
    }

    private String maxmindLocation(String ip) {
        try {
            CityResponse response = reader().city(InetAddress.getByName(ip));
            String country = localizedName(response.getCountry().getNames(), response.getCountry().getName());
            if (country == null) {
                return null;
            }
            String city = localizedName(response.getCity().getNames(), response.getCity().getName());
            return city == null ? country : country + " " + city;
        } catch (GeoIp2Exception ex) {
            return null;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to resolve IP location: " + ip, ex);
        }
    }

    private DatabaseReader reader() {
        DatabaseReader current = reader;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (reader == null) {
                if (databasePath == null || databasePath.isBlank()) {
                    throw new IllegalStateException("MaxMind database path is not configured: iam.geoip.database-path");
                }
                try {
                    reader = new DatabaseReader.Builder(new File(databasePath)).build();
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to open MaxMind database: " + databasePath, ex);
                }
            }
            return reader;
        }
    }

    private String localizedName(java.util.Map<String, String> names, String fallback) {
        String localized = names.get(CHINESE_LOCALE);
        return localized == null ? fallback : localized;
    }

    private String systemLocation(String ip) {
        if ("127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return "本机";
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || isPrivate172(ip)) {
            return "内网";
        }
        return "公网";
    }

    private boolean isPrivate172(String ip) {
        if (!ip.startsWith("172.")) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
