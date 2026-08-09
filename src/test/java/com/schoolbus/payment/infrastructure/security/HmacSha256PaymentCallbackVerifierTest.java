package com.schoolbus.payment.infrastructure.security;

import com.schoolbus.payment.api.InvalidPaymentSignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacSha256PaymentCallbackVerifierTest {

    private static final String SECRET = "test-payment-secret";

    @Test
    void shouldAcceptValidRawBodySignature() throws Exception {
        String body = "{\"paymentNumber\":\"p-1\",\"amount\":5.50}";
        String signature = sign(body);
        var verifier = new HmacSha256PaymentCallbackVerifier(SECRET);

        assertThatCode(() -> verifier.verify(body, "sha256=" + signature))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTamperedBody() throws Exception {
        String signature = sign("original");
        var verifier = new HmacSha256PaymentCallbackVerifier(SECRET);

        assertThatThrownBy(() -> verifier.verify("tampered", signature))
                .isInstanceOf(InvalidPaymentSignatureException.class);
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
