package com.schoolbus.paymentservice.infrastructure.security;

import com.schoolbus.paymentservice.api.PaymentServiceException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacSha256PaymentCallbackVerifierTest {

    private static final String SECRET = "payment-test-secret-2026";

    @Test
    void acceptsValidSignatureWithPrefix() throws Exception {
        HmacSha256PaymentCallbackVerifier verifier =
                new HmacSha256PaymentCallbackVerifier(SECRET);
        String body = "{\"paymentNumber\":\"p-1\"}";

        verifier.verify(body, "sha256=" + sign(body));
    }

    @Test
    void rejectsInvalidSignature() {
        HmacSha256PaymentCallbackVerifier verifier =
                new HmacSha256PaymentCallbackVerifier(SECRET);

        assertThatThrownBy(() -> verifier.verify("{}", "sha256=00"))
                .isInstanceOf(PaymentServiceException.class)
                .hasMessageContaining("signature is invalid");
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));
        return HexFormat.of().formatHex(
                mac.doFinal(body.getBytes(StandardCharsets.UTF_8))
        );
    }
}
