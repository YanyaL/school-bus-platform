package com.schoolbus.paymentservice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.paymentservice.application.ConfirmPaymentResult;
import com.schoolbus.paymentservice.application.PaymentConfirmationApplicationService;
import com.schoolbus.paymentservice.application.PaymentConfirmationOutcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentCallbackControllerTest {

    @Test
    void verifiesRawBodyBeforeReturningJsonSafePaymentId() {
        PaymentCallbackVerifier verifier = mock(PaymentCallbackVerifier.class);
        PaymentConfirmationApplicationService service = mock(
                PaymentConfirmationApplicationService.class
        );
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        PaymentCallbackController controller = new PaymentCallbackController(
                verifier,
                service,
                objectMapper
        );
        String paymentNumber = UUID.randomUUID().toString();
        String bookingNumber = UUID.randomUUID().toString();
        String body = """
                {
                  "requestNumber":"request-1",
                  "paymentNumber":"%s",
                  "bookingNumber":"%s",
                  "amount":12.50,
                  "paidAt":"2026-08-17T10:00:00Z"
                }
                """.formatted(paymentNumber, bookingNumber);
        when(service.confirmPayment(any())).thenReturn(
                new ConfirmPaymentResult(
                        9_007_199_254_740_993L,
                        paymentNumber,
                        bookingNumber,
                        new BigDecimal("12.50"),
                        PaymentConfirmationOutcome.CONFIRMED,
                        Instant.parse("2026-08-17T10:00:00Z")
                )
        );

        ApiResponse<PaymentCallbackController.PaymentCallbackResponse> response =
                controller.callback("sha256=signature", body);

        assertThat(response.data().paymentId())
                .isEqualTo("9007199254740993");
        var order = inOrder(verifier, service);
        order.verify(verifier).verify(body, "sha256=signature");
        order.verify(service).confirmPayment(any());
    }
}
