package com.schoolbus.paymentservice.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.paymentservice.application.ConfirmPaymentCommand;
import com.schoolbus.paymentservice.application.ConfirmPaymentResult;
import com.schoolbus.paymentservice.application.PaymentConfirmationApplicationService;
import com.schoolbus.paymentservice.application.PaymentConfirmationOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/payments")
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
        ConfirmPaymentResult result;
        try {
            result = service.confirmPayment(request.toCommand());
        } catch (IllegalArgumentException exception) {
            throw new PaymentServiceException(
                    "MALFORMED_PAYMENT_CALLBACK",
                    "payment callback payload is invalid",
                    HttpStatus.BAD_REQUEST
            );
        }
        return ApiResponse.success(PaymentCallbackResponse.from(result));
    }

    private PaymentCallbackRequest deserialize(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, PaymentCallbackRequest.class);
        } catch (JsonProcessingException exception) {
            throw new PaymentServiceException(
                    "MALFORMED_PAYMENT_CALLBACK",
                    "payment callback payload is invalid",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    public record PaymentCallbackRequest(
            String requestNumber,
            String paymentNumber,
            String bookingNumber,
            BigDecimal amount,
            Instant paidAt
    ) {
        ConfirmPaymentCommand toCommand() {
            return new ConfirmPaymentCommand(
                    requestNumber,
                    paymentNumber,
                    bookingNumber,
                    amount,
                    paidAt
            );
        }
    }

    public record PaymentCallbackResponse(
            String paymentId,
            String paymentNumber,
            String bookingNumber,
            BigDecimal amount,
            PaymentConfirmationOutcome outcome,
            Instant paidAt
    ) {
        static PaymentCallbackResponse from(ConfirmPaymentResult result) {
            return new PaymentCallbackResponse(
                    Long.toString(result.paymentId()),
                    result.paymentNumber(),
                    result.bookingNumber(),
                    result.amount(),
                    result.outcome(),
                    result.paidAt()
            );
        }
    }
}
