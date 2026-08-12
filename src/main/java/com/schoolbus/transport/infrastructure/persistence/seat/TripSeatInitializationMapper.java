package com.schoolbus.transport.infrastructure.persistence.seat;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TripSeatInitializationMapper {

    int countByTripId(@Param("tripId") Long tripId);

    int insertSeats(
            @Param("tripId") Long tripId,
            @Param("seatNumbers") List<String> seatNumbers,
            @Param("createdAt") LocalDateTime createdAt
    );
}
