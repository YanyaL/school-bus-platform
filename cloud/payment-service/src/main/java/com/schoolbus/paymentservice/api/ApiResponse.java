package com.schoolbus.paymentservice.api;

import org.slf4j.MDC;

import java.time.Instant;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                "OK",
                "success",
                data,
                MDC.get("traceId"),
                Instant.now()
        );
    }
}
