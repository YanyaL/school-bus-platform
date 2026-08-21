package com.schoolbus.bookingservice.application.booking;

public class BookingExpirationMessageConflictException
        extends RuntimeException {

    public BookingExpirationMessageConflictException(long bookingId) {
        super("booking expiration message conflicts with booking " + bookingId);
    }
}
