package com.schoolbus.paymentservice.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school-bus.payment.migration")
public record PaymentMigrationProperties(
        PaymentBookingWriteMode bookingWriteMode
) {

    public PaymentMigrationProperties {
        bookingWriteMode = bookingWriteMode == null
                ? PaymentBookingWriteMode.EVENT
                : bookingWriteMode;
    }

    public boolean eventOnly() {
        return bookingWriteMode == PaymentBookingWriteMode.EVENT;
    }
}
