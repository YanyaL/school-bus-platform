package com.schoolbus.shared.api;

import com.schoolbus.shared.web.TraceContext;

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
                TraceContext.currentTraceId(),
                Instant.now()
        );
    }
}
