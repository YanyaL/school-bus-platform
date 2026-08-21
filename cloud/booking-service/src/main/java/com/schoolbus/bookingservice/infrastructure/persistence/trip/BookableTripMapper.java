package com.schoolbus.bookingservice.infrastructure.persistence.trip;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface BookableTripMapper {

    @Select("""
            SELECT id, trip_no, price, departure_time, booking_deadline, status
             FROM transport_trip
             WHERE trip_no = #{tripNo}
             LIMIT 1
             FOR SHARE
            """)
    TripRow selectByTripNumber(@Param("tripNo") String tripNo);

    record TripRow(
            long id,
            String tripNo,
            BigDecimal price,
            LocalDateTime departureTime,
            LocalDateTime bookingDeadline,
            String status
    ) {
    }
}
