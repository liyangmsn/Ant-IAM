package com.antiam.service;

import com.antiam.common.TokenSupport;
import com.antiam.domain.MfaChallenge;
import com.antiam.domain.MfaFactor;
import com.antiam.domain.MfaFactorType;
import com.antiam.domain.UserAccount;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * MFA 验证码生成、投递与校验，供登录二次验证和个人中心 MFA 管理共用。
 */
@Service
@RequiredArgsConstructor
public class MfaVerificationService {

    private static final int TOTP_STEP_SECONDS = 30;
    private static final int TOTP_WINDOW = 1;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TokenSupport tokens;
    private final SmsVerificationService smsVerificationService;
    private final MailDeliveryService mailDeliveryService;

    @Value("${iam.mfa.issuer:Js IAM}")
    private String issuer;

    /**
     * 可用于登录二次验证的因子：已启用、已验证，且短信因子要求用户已绑定手机号。
     * WebAuthn 仍为回显挑战码的原型实现，不能作为登录第二因子。
     */
    public static boolean isLoginCapable(MfaFactor factor) {
        if (!factor.isEnabled() || !factor.isVerified()) {
            return false;
        }
        return switch (factor.getType()) {
            case TOTP, EMAIL -> true;
            case SMS -> hasText(factor.getUser().getMobile());
            case WEBAUTHN, RECOVERY_CODE -> false;
        };
    }

    /**
     * 恢复码因子是否仍有可用恢复码。
     */
    public static boolean hasRecoveryCodes(MfaFactor factor) {
        return factor.getType() == MfaFactorType.RECOVERY_CODE
            && factor.isEnabled()
            && hasText(factor.getSecret());
    }

    /**
     * 为挑战生成并投递一次性验证码，返回需要回显给调用方的验证码（仅 WebAuthn 原型因子）。
     */
    public IssuedCode issueCode(UserAccount user, MfaFactor factor) {
        return switch (factor.getType()) {
            case TOTP, RECOVERY_CODE -> new IssuedCode(factor.getType().name().toLowerCase(Locale.ROOT), null);
            case SMS -> {
                if (!hasText(user.getMobile())) {
                    throw new IllegalArgumentException("短信 MFA 需要先绑定手机号");
                }
                String code = smsVerificationService.deliverCode(user.getMobile(), "MFA");
                yield new IssuedCode(tokens.sha256(code), null);
            }
            case EMAIL -> {
                if (!hasText(user.getEmail())) {
                    throw new IllegalArgumentException("邮件 MFA 需要先设置邮箱");
                }
                String code = generateNumericCode();
                mailDeliveryService.send("login_verify", user.getEmail(), Map.of("code", code, "user_email", user.getEmail()));
                yield new IssuedCode(tokens.sha256(code), null);
            }
            case WEBAUTHN -> {
                // WebAuthn 仍为原型实现，挑战码直接返回给客户端。
                String code = generateNumericCode();
                yield new IssuedCode(tokens.sha256(code), code);
            }
        };
    }

    /**
     * 校验挑战验证码：TOTP 防重放，恢复码一次性消费，其余类型比较验证码摘要。
     */
    public boolean verify(MfaChallenge challenge, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String normalized = code.trim();
        MfaFactor factor = challenge.getFactor();
        return switch (factor.getType()) {
            case TOTP -> verifyTotp(factor, normalized, Instant.now());
            case RECOVERY_CODE -> consumeRecoveryCode(factor, normalized);
            case SMS, EMAIL, WEBAUTHN -> tokens.sha256(normalized).equals(challenge.getCodeHash());
        };
    }

    /**
     * 校验并消费一个恢复码。
     */
    public boolean consumeRecoveryCode(MfaFactor factor, String code) {
        if (code == null || code.isBlank() || !factor.isEnabled()) {
            return false;
        }
        String hash = tokens.sha256(code.trim().toUpperCase(Locale.ROOT));
        List<String> remaining = hasText(factor.getSecret())
            ? Arrays.stream(factor.getSecret().split("\\R")).filter(item -> !item.isBlank()).toList()
            : List.of();
        if (!remaining.contains(hash)) {
            return false;
        }
        factor.replaceSecret(remaining.stream()
            .filter(item -> !item.equals(hash))
            .collect(Collectors.joining("\n")));
        return true;
    }

    public String generateTotpSecret() {
        byte[] bytes = new byte[20];
        SECURE_RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    /**
     * 规范化并校验 TOTP Base32 密钥，至少 80 位（16 个 Base32 字符）。
     */
    public String normalizeTotpSecret(String secret) {
        String normalized = secret.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        if (normalized.length() < 16 || normalized.chars().anyMatch(ch -> BASE32_ALPHABET.indexOf(ch) < 0)) {
            throw new IllegalArgumentException("TOTP 密钥必须是至少 16 位的 Base32 字符串");
        }
        return normalized;
    }

    public String provisioningUri(MfaFactor factor) {
        String label = url(issuer + ":" + factor.getUser().getUsername());
        return "otpauth://totp/" + label + "?secret=" + factor.getSecret() + "&issuer=" + url(issuer)
            + "&algorithm=SHA1&digits=6&period=" + TOTP_STEP_SECONDS;
    }

    public String generateRecoveryCode() {
        StringBuilder builder = new StringBuilder();
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int index = 0; index < 12; index++) {
            if (index > 0 && index % 4 == 0) {
                builder.append('-');
            }
            builder.append(alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    public String deliveryHint(MfaFactor factor) {
        UserAccount user = factor.getUser();
        return switch (factor.getType()) {
            case SMS -> maskMobile(user.getMobile());
            case EMAIL -> maskEmail(user.getEmail());
            case TOTP -> "authenticator";
            case WEBAUTHN -> "webauthn";
            case RECOVERY_CODE -> "recovery-code";
        };
    }

    private boolean verifyTotp(MfaFactor factor, String code, Instant now) {
        if (!hasText(factor.getSecret()) || !code.matches("\\d{6}")) {
            return false;
        }
        long counter = now.getEpochSecond() / TOTP_STEP_SECONDS;
        Long lastUsed = factor.getLastUsedCounter();
        for (int offset = -TOTP_WINDOW; offset <= TOTP_WINDOW; offset++) {
            long candidate = counter + offset;
            if (lastUsed != null && candidate <= lastUsed) {
                // 已使用过的时间步不再接受，防止验证码重放。
                continue;
            }
            if (code.equals(totp(factor.getSecret(), candidate))) {
                factor.markTotpCounterUsed(candidate);
                return true;
            }
        }
        return false;
    }

    private String totp(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return null;
        }
    }

    private String generateNumericCode() {
        return String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String base32(byte[] bytes) {
        StringBuilder encoded = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                encoded.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 31));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            encoded.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 31));
        }
        return encoded.toString();
    }

    private static byte[] base32Decode(String value) {
        String normalized = value.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        for (char ch : normalized.toCharArray()) {
            int index = BASE32_ALPHABET.indexOf(ch);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid TOTP secret");
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return output.toByteArray();
    }

    private static String maskMobile(String mobile) {
        if (!hasText(mobile) || mobile.length() < 7) {
            return "sms";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private static String maskEmail(String email) {
        if (!hasText(email) || email.indexOf('@') <= 0) {
            return "email";
        }
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 签发结果：codeHash 保存到挑战记录，echoCode 仅在需要回显时非空。
     */
    public record IssuedCode(String codeHash, String echoCode) {
    }
}
