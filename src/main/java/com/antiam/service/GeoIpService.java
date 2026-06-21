package com.antiam.service;

import com.antiam.dto.SettingDtos.GeoIpLookupResponse;
import com.antiam.dto.SettingDtos.IntegrationTestResponse;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeoIpService {

    private static final String MAXMIND_DOWNLOAD_URL =
        "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&suffix=tar.gz&license_key=";

    private final SettingJsonSupport settings;
    private final ClientMetadataService clientMetadataService;

    public IntegrationTestResponse updateMaxmindDatabase(String licenseKey, String databasePath) {
        String resolvedLicenseKey = firstPresent(licenseKey, settings.string("geoip.licenseKey", ""));
        String resolvedDatabasePath = firstPresent(databasePath, settings.string("geoip.databasePath", "data/GeoLite2-City.mmdb"));
        if (resolvedLicenseKey.isBlank()) {
            throw new IllegalArgumentException("MaxMind licenseKey is required");
        }
        try {
            byte[] archive = download(resolvedLicenseKey);
            Path target = Path.of(resolvedDatabasePath).toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            extractMmdb(archive, target);
            return new IntegrationTestResponse(true, "GeoIP 数据库已更新：" + target);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("GeoIP database update failed: " + ex.getMessage(), ex);
        }
    }

    public GeoIpLookupResponse lookup(String ip) {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("IP is required");
        }
        String provider = settings.string("geoip.provider", "system");
        if ("maxmind".equalsIgnoreCase(provider)) {
            return maxmind(ip);
        }
        String location = clientMetadataService.location(ip);
        return new GeoIpLookupResponse(ip, "system", "", "", "", location);
    }

    private GeoIpLookupResponse maxmind(String ip) {
        String databasePath = settings.string("geoip.databasePath", "");
        if (databasePath.isBlank()) {
            throw new IllegalArgumentException("MaxMind databasePath is required");
        }
        File database = new File(databasePath);
        if (!database.isFile()) {
            throw new IllegalArgumentException("MaxMind database file does not exist: " + databasePath);
        }
        try (DatabaseReader reader = new DatabaseReader.Builder(database).build()) {
            CityResponse response = reader.city(InetAddress.getByName(ip));
            String country = response.getCountry().getNames().getOrDefault("zh-CN", response.getCountry().getName());
            String province = response.getMostSpecificSubdivision().getNames()
                .getOrDefault("zh-CN", response.getMostSpecificSubdivision().getName());
            String city = response.getCity().getNames().getOrDefault("zh-CN", response.getCity().getName());
            return new GeoIpLookupResponse(ip, "maxmind", blank(country), blank(province), blank(city), join(country, province, city));
        } catch (Exception ex) {
            throw new IllegalArgumentException("GeoIP lookup failed: " + ex.getMessage(), ex);
        }
    }

    private byte[] download(String licenseKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(MAXMIND_DOWNLOAD_URL + licenseKey))
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("MaxMind download failed: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private void extractMmdb(byte[] archive, Path target) throws Exception {
        try (
            ByteArrayInputStream input = new ByteArrayInputStream(archive);
            GzipCompressorInputStream gzip = new GzipCompressorInputStream(input);
            TarArchiveInputStream tar = new TarArchiveInputStream(gzip)
        ) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".mmdb")) {
                    Files.copy(tar, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IllegalArgumentException("MaxMind archive does not contain a .mmdb file");
    }

    private String firstPresent(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String join(String country, String province, String city) {
        return String.join(" ", blank(country), blank(province), blank(city)).trim();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
