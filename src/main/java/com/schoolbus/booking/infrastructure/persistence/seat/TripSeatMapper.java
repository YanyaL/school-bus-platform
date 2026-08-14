package com.schoolbus.booking.infrastructure.persistence.seat;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface TripSeatMapper {

    int tryLockSeat(
            @Param("tripId") Long tripId,
            @Param("seatNumber") String seatNumber,
            @Param("bookingNumber") String bookingNumber,
            @Param("userId") Long userId,
            @Param("lockExpiresAt") LocalDateTime lockExpiresAt,
            @Param("lockedAt") LocalDateTime lockedAt
    );

    int releaseSeat(
            @Param("tripId") Long tripId,
            @Param("seatNumber") String seatNumber,
            @Param("bookingNumber") String bookingNumber,
            @Param("releasedAt") LocalDateTime releasedAt
    );

    int releaseSoldSeat(
            @Param("tripId") Long tripId,
            @Param("seatNumber") String seatNumber,
            @Param("bookingNumber") String bookingNumber,
            @Param("releasedAt") LocalDateTime releasedAt
    );

    int confirmSeatSold(
            @Param("tripId") Long tripId,
            @Param("seatNumber") String seatNumber,
            @Param("bookingNumber") String bookingNumber,
            @Param("soldAt") LocalDateTime soldAt
    );
}
