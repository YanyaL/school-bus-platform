package com.schoolbus.bookingservice.application.booking;

public interface BookingExpirationOutboxPort {

    void append(BookingPaymentDeadlineEvent event);
}
