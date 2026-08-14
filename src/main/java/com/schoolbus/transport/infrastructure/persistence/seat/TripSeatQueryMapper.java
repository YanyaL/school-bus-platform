package com.schoolbus.transport.infrastructure.persistence.seat;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TripSeatQueryMapper {

    int insertAvailableSeats(
            @Param("tripId") Long tripId,
            @Param("seatNumbers") List<String> seatNumbers,
            @Param("initializedAt") LocalDateTime initializedAt
    );

    List<TripSeatStatusDataObject> selectSeatStatusesByTripId(
            @Param("tripId") Long tripId
    );
}
