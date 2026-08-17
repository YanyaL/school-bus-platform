package com.schoolbus.payment.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.payment.application.ConfirmPaymentResult;
import com.schoolbus.payment.application.PaymentConfirmationApplicationService;
import com.schoolbus.payment.config.ConditionalOnEmbeddedPayment;
import com.schoolbus.shared.api.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/payments")
@Profile("!test")
@ConditionalOnEmbeddedPayment
public class PaymentCallbackController {

    public static final String SIGNATURE_HEADER = "X-Payment-Signature";

    private final PaymentCallbackVerifier verifier;
    private final PaymentConfirmationApplicationService service;
    private final ObjectMapper objectMapper;

    public PaymentCallbackController(
            PaymentCallbackVerifier verifier,
            PaymentConfirmationApplicationService service,
            ObjectMapper objectMapper
    ) {
        this.verifier = Objects.requireNonNull(verifier);
        this.service = Objects.requireNonNull(service);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @PostMapping("/callback")
    public ApiResponse<PaymentCallbackResponse> callback(
            @RequestHeader(SIGNATURE_HEADER) String signature,
            @RequestBody String rawBody
    ) {
        verifier.verify(rawBody, signature);
        PaymentCallbackRequest request = deserialize(rawBody);
        ConfirmPaymentResult result = service.confirmPayment(
                request.toCommand()
        );
        return ApiResponse.success(PaymentCallbackResponse.from(result));
    }

    private PaymentCallbackRequest deserialize(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, PaymentCallbackRequest.class);
        } catch (JsonProcessingException exception) {
            throw new MalformedPaymentCallbackException();
        }
    }
}
