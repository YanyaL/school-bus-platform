package com.schoolbus.iamservice.api;

public record FieldErrorDetail(
        String field,
        String reason
) {
}
