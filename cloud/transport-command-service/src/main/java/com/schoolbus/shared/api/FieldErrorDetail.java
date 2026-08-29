package com.schoolbus.shared.api;

public record FieldErrorDetail(
        String field,
        String reason
) {
}
