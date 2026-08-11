package com.schoolbus.booking.application.booking;

public interface BookingExpirationOutboxPort {

    void append(BookingPaymentDeadlineEvent event);
}
