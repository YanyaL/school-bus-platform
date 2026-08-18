package com.schoolbus.paymentservice.domain;

public record BookingNumber(String value) { public BookingNumber { if (value == null || value.isBlank()) throw new IllegalArgumentException("value blank"); value = value.strip(); } public static BookingNumber of(String value) { return new BookingNumber(value); } public String toString() { return value; } }
