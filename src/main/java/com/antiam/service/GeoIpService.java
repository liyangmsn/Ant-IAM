package com.antiam.service;

import com.antiam.domain.SystemSetting;
import com.antiam.repository.SystemSettingRepository;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeoIpService {

    private static final String SETTING_KEY = "geoip.provider";
    private static final String DATABASE_PATH_SETTING_KEY = "geoip.databasePath";
    private static final String MAXMIND_PROVIDER = "maxmind";
    private static final String SYSTEM_PROVIDER = "system";
    private static final String CHINESE_LOCALE = "zh-CN";
    private static final String MAXMIND_DOWNLOAD_URL =
        "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&suffix=tar.gz&license_key=";
    private static final String DATABASE_ENTRY_SUFFIX = "GeoLite2-City.mmdb";

    private final SystemSettingRepository settings;
    private final String defaultDatabasePath;
    private volatile DatabaseReader reader;
    private volatile String readerPath;

    public GeoIpService(
        SystemSettingRepository settings,
        @Value("${iam.geoip.database-path:}") String defaultDatabasePath
    ) {
        this.settings = settings;
        this.defaultDatabasePath = defaultDatabasePath;
    }

    public record GeoIpLocation(String ip, String provider, String country, String province, String city, String location) {
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

    /**
     * 按当前提供商解析 IP，返回国家、省份、城市等明细，用于控制台解析测试。
     */
    public GeoIpLocation lookup(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("IP 地址不能为空");
        }
        String ip = ipAddress.trim();
        if (!usesMaxmind()) {
            return new GeoIpLocation(ip, SYSTEM_PROVIDER, null, null, null, systemLocation(ip));
        }
        try {
            CityResponse response = reader().city(InetAddress.getByName(ip));
            String country = localizedName(response.getCountry().getNames(), response.getCountry().getName());
            String province = localizedName(
                response.getMostSpecificSubdivision().getNames(),
                response.getMostSpecificSubdivision().getName());
            String city = localizedName(response.getCity().getNames(), response.getCity().getName());
            return new GeoIpLocation(ip, MAXMIND_PROVIDER, country, province, city, join(country, province, city));
        } catch (GeoIp2Exception ex) {
            return new GeoIpLocation(ip, MAXMIND_PROVIDER, null, null, null, null);
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法解析 IP 地址：" + ip, ex);
        }
    }

    /**
     * 使用 MaxMind License Key 下载 GeoLite2-City 数据库到指定路径，并重新加载。
     */
    public String updateDatabase(String licenseKey, String requestedPath) {
        if (licenseKey == null || licenseKey.isBlank()) {
            throw new IllegalArgumentException("请输入 MaxMind License Key");
        }
        String targetPath = requestedPath == null || requestedPath.isBlank() ? databasePath() : requestedPath.trim();
        if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("请输入 MaxMind 数据库路径");
        }
        Path target = Path.of(targetPath).toAbsolutePath();
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                MAXMIND_DOWNLOAD_URL + URLEncoder.encode(licenseKey.trim(), StandardCharsets.UTF_8)))
            .timeout(Duration.ofSeconds(80))
            .GET()
            .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IllegalArgumentException("MaxMind 下载失败，HTTP 状态码 " + response.statusCode() + "，请检查 License Key");
            }
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Path temporary = Files.createTempFile(target.getParent() == null ? Path.of(".") : target.getParent(), "geoip-", ".mmdb");
            try (InputStream body = new GZIPInputStream(response.body())) {
                extractDatabase(body, temporary);
                // 校验下载的数据库可以被正常打开，避免覆盖可用文件。
                new DatabaseReader.Builder(temporary.toFile()).build().close();
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("GeoIP 数据库下载失败：" + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GeoIP database download interrupted", ex);
        }
        resetReader();
        return "GeoLite2-City 数据库已更新：" + target;
    }

    // 从 tar 流中取出 GeoLite2-City.mmdb 条目。
    private void extractDatabase(InputStream tar, Path destination) throws IOException {
        byte[] header = new byte[512];
        while (tar.readNBytes(header, 0, 512) == 512) {
            String name = new String(header, 0, 100, StandardCharsets.US_ASCII).trim().replace("\0", "");
            if (name.isEmpty()) {
                break;
            }
            String sizeText = new String(header, 124, 12, StandardCharsets.US_ASCII).replace("\0", "").trim();
            long size = sizeText.isEmpty() ? 0 : Long.parseLong(sizeText, 8);
            long padded = (size + 511) / 512 * 512;
            if (name.endsWith(DATABASE_ENTRY_SUFFIX)) {
                Files.copy(new BoundedInputStream(tar, size), destination, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            tar.skipNBytes(padded);
        }
        throw new IOException("压缩包中未找到 " + DATABASE_ENTRY_SUFFIX);
    }

    private boolean usesMaxmind() {
        return settings.findBySettingKey(SETTING_KEY)
            .map(SystemSetting::getSettingValue)
            .map(value -> MAXMIND_PROVIDER.equalsIgnoreCase(value.trim()))
            .orElse(false);
    }

    // 优先使用系统设置中的数据库路径，未配置时回退到 iam.geoip.database-path。
    private String databasePath() {
        return settings.findBySettingKey(DATABASE_PATH_SETTING_KEY)
            .map(SystemSetting::getSettingValue)
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .orElse(defaultDatabasePath);
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
        String path = databasePath();
        DatabaseReader current = reader;
        if (current != null && path != null && path.equals(readerPath)) {
            return current;
        }
        synchronized (this) {
            if (reader == null || path == null || !path.equals(readerPath)) {
                if (path == null || path.isBlank()) {
                    throw new IllegalStateException("MaxMind database path is not configured: iam.geoip.database-path");
                }
                closeReader();
                try {
                    reader = new DatabaseReader.Builder(new File(path)).build();
                    readerPath = path;
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to open MaxMind database: " + path, ex);
                }
            }
            return reader;
        }
    }

    private synchronized void resetReader() {
        closeReader();
    }

    private void closeReader() {
        DatabaseReader current = reader;
        reader = null;
        readerPath = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // 旧的数据库句柄关闭失败不影响重新加载。
            }
        }
    }

    private String localizedName(Map<String, String> names, String fallback) {
        String localized = names.get(CHINESE_LOCALE);
        return localized == null ? fallback : localized;
    }

    private String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank() && builder.indexOf(part) < 0) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(part);
            }
        }
        return builder.isEmpty() ? null : builder.toString();
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

    private static final class BoundedInputStream extends InputStream {

        private final InputStream delegate;
        private long remaining;

        BoundedInputStream(InputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = delegate.read(buffer, offset, (int) Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() {
            // 不关闭底层 tar 流。
        }
    }
}
