package com.antiam.service;

import static com.antiam.dto.AuthenticationDtos.SendSmsCodeResponse;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmsVerificationService {

    @Value("${ant-iam.sms.fixed-code:666666}")
    private String fixedCode;

    @Value("${ant-iam.sms.code-ttl-seconds:300}")
    private long codeTtlSeconds;

    public SendSmsCodeResponse sendFixedCode(String mobile, String purpose) {
        String normalizedPurpose = normalizePurpose(purpose);
        // SMS4J is on the classpath for provider integration; fixed-code mode intentionally skips external delivery.
        return new SendSmsCodeResponse(mobile, normalizedPurpose, Instant.now().plusSeconds(codeTtlSeconds));
    }

    public void verify(String mobile, String purpose, String code) {
        if (mobile == null || mobile.isBlank()) {
            throw new IllegalArgumentException("Mobile is required");
        }
        if (code == null || !code.equals(fixedCode)) {
            throw new IllegalArgumentException("SMS verification code is invalid");
        }
        normalizePurpose(purpose);
    }

    private String normalizePurpose(String purpose) {
        return purpose == null || purpose.isBlank() ? "LOGIN" : purpose.trim().toUpperCase();
    }
}
