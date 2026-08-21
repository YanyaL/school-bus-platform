package com.schoolbus.bookingservice.infrastructure.identity;

import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.domain.order.BookingNumberGenerator;

import java.util.UUID;

public final class UuidBookingNumberGenerator
        implements BookingNumberGenerator {

    @Override
    public BookingNumber nextNumber() {
        return new BookingNumber(UUID.randomUUID());
    }
}
