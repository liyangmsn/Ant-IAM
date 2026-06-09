package com.antiam.domain;

public enum AuthenticationEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    MFA_CHALLENGE,
    MFA_SUCCESS,
    MFA_FAILURE,
    LOGOUT,
    TOKEN_ISSUED,
    TOKEN_REVOKED,
    CONSENT_GRANTED,
    CONSENT_REVOKED,
    RISK_ASSESSED
}
