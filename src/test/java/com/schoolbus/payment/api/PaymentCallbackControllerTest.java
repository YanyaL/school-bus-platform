package com.schoolbus.payment.api;

import com.schoolbus.payment.application.ConfirmPaymentCommand;
import com.schoolbus.payment.application.ConfirmPaymentResult;
import com.schoolbus.payment.application.PaymentConfirmationApplicationService;
import com.schoolbus.payment.application.PaymentConfirmationOutcome;
import com.schoolbus.shared.api.GlobalExceptionHandler;
import com.schoolbus.shared.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("web-test")
@WebMvcTest(PaymentCallbackController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PaymentCallbackControllerTest {

    private static final String BODY = """
            {
              "requestNumber": "callback-1",
              "paymentNumber": "77777777-7777-7777-7777-777777777777",
              "bookingNumber": "55555555-5555-5555-5555-555555555555",
              "amount": 5.50,
              "paidAt": "2026-08-08T00:10:00Z"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentCallbackVerifier verifier;

    @MockitoBean
    private PaymentConfirmationApplicationService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldAcceptSignedCallbackWithoutJwt() throws Exception {
        when(service.confirmPayment(any())).thenReturn(
                new ConfirmPaymentResult(
                        9001L,
                        "77777777-7777-7777-7777-777777777777",
                        "55555555-5555-5555-5555-555555555555",
                        new BigDecimal("5.50"),
                        PaymentConfirmationOutcome.CONFIRMED,
                        Instant.parse("2026-08-08T00:10:00Z")
                )
        );

        mockMvc.perform(post("/api/v1/payments/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PaymentCallbackController.SIGNATURE_HEADER, "signature")
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.outcome").value("CONFIRMED"));

        verify(verifier).verify(BODY, "signature");
        verify(service).confirmPayment(new ConfirmPaymentCommand(
                "callback-1",
                "77777777-7777-7777-7777-777777777777",
                "55555555-5555-5555-5555-555555555555",
                new BigDecimal("5.50"),
                Instant.parse("2026-08-08T00:10:00Z")
        ));
    }

    @Test
    void shouldRejectInvalidSignatureBeforeParsingOrBusinessLogic() throws Exception {
        doThrow(new InvalidPaymentSignatureException())
                .when(verifier).verify(BODY, "bad-signature");

        mockMvc.perform(post("/api/v1/payments/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PaymentCallbackController.SIGNATURE_HEADER, "bad-signature")
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_SIGNATURE"));

        verifyNoInteractions(service);
    }
}
