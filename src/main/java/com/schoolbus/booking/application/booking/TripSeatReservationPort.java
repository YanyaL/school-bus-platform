package com.schoolbus.booking.application.booking;

public interface TripSeatReservationPort {

    boolean tryLockSeat(SeatLockRequest request);

    boolean releaseSeat(SeatReleaseRequest request);

    boolean confirmSeatSold(SeatSaleRequest request);
}
