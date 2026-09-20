package com.antiam.service;

import static com.antiam.dto.AuthenticationDtos.SendSmsCodeResponse;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmsVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern VERIFICATION_CODE_PATTERN = Pattern.compile("\\d{4,8}");

    private final Map<VerificationKey, VerificationCode> codes = new ConcurrentHashMap<>();

    @Value("${ant-iam.sms.blend-id:fixed-code}")
    private String smsBlendId;

    @Value("${ant-iam.sms.code-ttl-seconds:300}")
    private long codeTtlSeconds;

    public SendSmsCodeResponse sendVerificationCode(String mobile, String purpose) {
        String normalizedMobile = normalizeMobile(mobile);
        String normalizedPurpose = normalizePurpose(purpose);
        String generatedCode = generateCode();
        SmsResponse response = smsBlend().sendMessage(normalizedMobile, generatedCode);
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("SMS verification code delivery failed");
        }
        Instant expiresAt = Instant.now().plusSeconds(codeTtlSeconds);
        codes.put(new VerificationKey(normalizedMobile, normalizedPurpose), new VerificationCode(resolveCode(response, generatedCode), expiresAt));
        return new SendSmsCodeResponse(normalizedMobile, normalizedPurpose, expiresAt);
    }

    public void verify(String mobile, String purpose, String code) {
        String normalizedMobile = normalizeMobile(mobile);
        String normalizedPurpose = normalizePurpose(purpose);
        VerificationKey key = new VerificationKey(normalizedMobile, normalizedPurpose);
        VerificationCode verificationCode = codes.get(key);
        if (verificationCode == null) {
            throw new IllegalArgumentException("SMS verification code is invalid");
        }
        if (verificationCode.isExpired(Instant.now())) {
            codes.remove(key);
            throw new IllegalArgumentException("SMS verification code is invalid");
        }
        if (code == null || !code.equals(verificationCode.code())) {
            throw new IllegalArgumentException("SMS verification code is invalid");
        }
        codes.remove(key);
    }

    private SmsBlend smsBlend() {
        SmsBlend blend = smsBlendId == null || smsBlendId.isBlank()
            ? SmsFactory.getSmsBlend()
            : SmsFactory.getSmsBlend(smsBlendId);
        if (blend == null) {
            throw new IllegalStateException("SMS channel is not configured: " + smsBlendId);
        }
        return blend;
    }

    private String resolveCode(SmsResponse response, String generatedCode) {
        Object data = response.getData();
        if (data instanceof String value && isVerificationCode(value)) {
            return value;
        }
        if (data instanceof Map<?, ?> values) {
            Object code = values.get("code");
            if (code instanceof String value && isVerificationCode(value)) {
                return value;
            }
        }
        return generatedCode;
    }

    private boolean isVerificationCode(String value) {
        return VERIFICATION_CODE_PATTERN.matcher(value).matches();
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    private String normalizeMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            throw new IllegalArgumentException("Mobile is required");
        }
        return mobile.trim();
    }

    private String normalizePurpose(String purpose) {
        return purpose == null || purpose.isBlank() ? "LOGIN" : purpose.trim().toUpperCase();
    }

    private record VerificationKey(String mobile, String purpose) {
    }

    private record VerificationCode(String code, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }
}
