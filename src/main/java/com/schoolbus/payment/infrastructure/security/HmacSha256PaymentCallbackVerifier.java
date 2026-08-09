package com.schoolbus.payment.infrastructure.security;

import com.schoolbus.payment.api.InvalidPaymentSignatureException;
import com.schoolbus.payment.api.PaymentCallbackVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

@Component
@Profile("!test")
public class HmacSha256PaymentCallbackVerifier
        implements PaymentCallbackVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public HmacSha256PaymentCallbackVerifier(
            @Value("${school-bus.payment.callback-secret}")
            String secret
    ) {
        String validated = Objects.requireNonNull(
                secret,
                "payment callback secret must not be null"
        ).strip();
        if (validated.isEmpty()) {
            throw new IllegalArgumentException(
                    "payment callback secret must not be blank"
            );
        }
        this.secret = validated.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void verify(String rawBody, String signature) {
        String body = Objects.requireNonNull(rawBody, "rawBody must not be null");
        byte[] suppliedSignature = decodeSignature(signature);
        byte[] expectedSignature = sign(body);
        if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
            throw new InvalidPaymentSignatureException();
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
            throw new InvalidPaymentSignatureException();
        }
        String normalized = signature.strip();
        if (normalized.startsWith("sha256=")) {
            normalized = normalized.substring("sha256=".length());
        }
        try {
            return HexFormat.of().parseHex(normalized);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentSignatureException();
        }
    }
}
