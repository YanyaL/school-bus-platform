package com.schoolbus.paymentservice.infrastructure.security;

import com.schoolbus.paymentservice.api.PaymentCallbackVerifier;
import com.schoolbus.paymentservice.api.PaymentServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

@Component
public class HmacSha256PaymentCallbackVerifier
        implements PaymentCallbackVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    public HmacSha256PaymentCallbackVerifier(
            @Value("${school-bus.payment.callback-secret}") String secret
    ) {
        String checked = Objects.requireNonNull(
                secret,
                "payment callback secret must not be null"
        ).strip();
        if (checked.length() < 16) {
            throw new IllegalArgumentException(
                    "payment callback secret must contain at least 16 characters"
            );
        }
        this.secret = checked.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void verify(String rawBody, String signature) {
        byte[] supplied = decodeSignature(signature);
        byte[] expected = sign(Objects.requireNonNull(rawBody));
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw invalidSignature();
        }
    }

    private byte[] sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private byte[] decodeSignature(String signature) {
        if (signature == null) {
            throw invalidSignature();
        }
        String normalized = signature.strip();
        if (normalized.startsWith("sha256=")) {
            normalized = normalized.substring("sha256=".length());
        }
        try {
            return HexFormat.of().parseHex(normalized);
        } catch (IllegalArgumentException exception) {
            throw invalidSignature();
        }
    }

    private PaymentServiceException invalidSignature() {
        return new PaymentServiceException(
                "INVALID_PAYMENT_SIGNATURE",
                "payment callback signature is invalid",
                HttpStatus.UNAUTHORIZED
        );
    }
}
