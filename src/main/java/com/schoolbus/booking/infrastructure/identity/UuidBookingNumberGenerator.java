package com.schoolbus.booking.infrastructure.identity;

import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingNumberGenerator;

import java.util.UUID;

public final class UuidBookingNumberGenerator
        implements BookingNumberGenerator {

    @Override
    public BookingNumber nextNumber() {
        return new BookingNumber(UUID.randomUUID());
    }
}
