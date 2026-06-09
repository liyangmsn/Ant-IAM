package com.antiam.domain;

public enum MfaFactorType {
    TOTP,
    SMS,
    EMAIL,
    WEBAUTHN,
    RECOVERY_CODE
}
