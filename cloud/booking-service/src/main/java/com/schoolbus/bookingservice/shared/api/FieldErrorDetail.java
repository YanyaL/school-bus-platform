package com.schoolbus.bookingservice.shared.api;

public record FieldErrorDetail(
        String field,
        String reason
) {
}
