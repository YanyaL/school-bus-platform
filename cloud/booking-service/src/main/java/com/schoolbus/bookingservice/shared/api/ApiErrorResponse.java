package com.schoolbus.bookingservice.shared.api;

import com.schoolbus.bookingservice.shared.web.TraceContext;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<FieldErrorDetail> details,
        String traceId,
        Instant timestamp
) {

    public static ApiErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> details) {
        return new ApiErrorResponse(
                errorCode.name(),
                errorCode.defaultMessage(),
                List.copyOf(details),
                TraceContext.currentTraceId(),
                Instant.now()
        );
    }

    public static ApiErrorResponse of(BusinessException exception) {
        return new ApiErrorResponse(
                exception.errorCode().name(),
                exception.getMessage(),
                List.of(),
                TraceContext.currentTraceId(),
                Instant.now()
        );
    }
}
