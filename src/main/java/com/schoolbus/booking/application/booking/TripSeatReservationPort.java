package com.schoolbus.booking.application.booking;

public interface TripSeatReservationPort {

    boolean tryLockSeat(SeatLockRequest request);
}
