package com.schoolbus.paymentservice.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(
            GlobalExceptionHandler.class
    );

    @ExceptionHandler(PaymentServiceException.class)
    ResponseEntity<ApiErrorResponse> handlePaymentError(
            PaymentServiceException exception
    ) {
        return ResponseEntity.status(exception.status()).body(
                ApiErrorResponse.of(exception.code(), exception.getMessage())
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiErrorResponse> handleMissingHeader(
            MissingRequestHeaderException exception
    ) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "VALIDATION_ERROR",
                "required request header is missing: "
                        + exception.getHeaderName()
        ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled payment request exception", exception);
        return ResponseEntity.internalServerError().body(ApiErrorResponse.of(
                "INTERNAL_ERROR",
                "internal server error"
        ));
    }

    record ApiErrorResponse(
            String code,
            String message,
            String traceId,
            Instant timestamp
    ) {
        static ApiErrorResponse of(String code, String message) {
            return new ApiErrorResponse(
                    code,
                    message,
                    MDC.get("traceId"),
                    Instant.now()
            );
        }
    }
}
