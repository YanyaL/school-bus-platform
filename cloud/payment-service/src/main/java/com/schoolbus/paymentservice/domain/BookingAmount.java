package com.schoolbus.paymentservice.domain;

import java.math.BigDecimal;
public record BookingAmount(BigDecimal amount) { public BookingAmount { if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("amount invalid"); } public static BookingAmount of(String value) { return new BookingAmount(new BigDecimal(value)); } }
