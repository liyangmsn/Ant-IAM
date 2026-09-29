package com.antiam.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * 以 DER 手工编码生成 SHA256withRSA 自签名 X.509 v1 证书，用于在 SAML 元数据中发布签名公钥。
 * 输入相同则输出相同（RSA PKCS#1 v1.5 签名是确定性的），因此可按签名密钥重复生成而不需落库。
 */
public final class SelfSignedCertificates {

    private static final byte[] SHA256_WITH_RSA = {0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B};
    private static final byte[] COMMON_NAME = {0x06, 0x03, 0x55, 0x04, 0x03};
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'");
    private static final DateTimeFormatter GENERALIZED_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'");

    private SelfSignedCertificates() {
    }

    public static X509Certificate create(
        PublicKey publicKey,
        PrivateKey privateKey,
        String commonName,
        String serialSeed,
        Instant notBefore,
        Instant notAfter
    ) {
        try {
            byte[] algorithm = sequence(SHA256_WITH_RSA, new byte[] {0x05, 0x00});
            byte[] name = sequence(set(sequence(COMMON_NAME, tlv(0x0C, commonName.getBytes(StandardCharsets.UTF_8)))));
            byte[] tbs = sequence(
                tlv(0x02, serial(serialSeed)),
                algorithm,
                name,
                sequence(time(notBefore), time(notAfter)),
                name,
                publicKey.getEncoded());
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(tbs);
            byte[] signature = signer.sign();
            byte[] bitString = new byte[signature.length + 1];
            System.arraycopy(signature, 0, bitString, 1, signature.length);
            byte[] certificate = sequence(tbs, algorithm, tlv(0x03, bitString));
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificate));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to create self-signed certificate", ex);
        }
    }

    // 序列号取种子摘要的前 16 字节，并保证编码为正整数。
    private static byte[] serial(String seed) throws GeneralSecurityException {
        byte[] digest = Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)), 16);
        digest[0] &= 0x7F;
        if (digest[0] == 0) {
            digest[0] = 0x01;
        }
        return digest;
    }

    // RFC 5280：2050 年之前使用 UTCTime，之后使用 GeneralizedTime。
    private static byte[] time(Instant instant) {
        ZonedDateTime value = instant.atZone(ZoneOffset.UTC);
        if (value.getYear() < 2050) {
            return tlv(0x17, UTC_TIME.format(value).getBytes(StandardCharsets.US_ASCII));
        }
        return tlv(0x18, GENERALIZED_TIME.format(value).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] sequence(byte[]... parts) {
        return tlv(0x30, concat(parts));
    }

    private static byte[] set(byte[]... parts) {
        return tlv(0x31, concat(parts));
    }

    private static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int length = value.length;
        if (length < 0x80) {
            out.write(length);
        } else if (length < 0x100) {
            out.write(0x81);
            out.write(length);
        } else if (length < 0x10000) {
            out.write(0x82);
            out.write(length >> 8);
            out.write(length);
        } else {
            out.write(0x83);
            out.write(length >> 16);
            out.write(length >> 8);
            out.write(length);
        }
        out.writeBytes(value);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
