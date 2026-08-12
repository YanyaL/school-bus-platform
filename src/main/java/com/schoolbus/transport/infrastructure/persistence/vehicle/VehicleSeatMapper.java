package com.schoolbus.transport.infrastructure.persistence.vehicle;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VehicleSeatMapper {

    int insertSeats(
            @Param("vehicleId") long vehicleId,
            @Param("seatNumbers") List<String> seatNumbers,
            @Param("createdAt") LocalDateTime createdAt
    );

    List<String> selectSeatNumbersByVehicleId(
            @Param("vehicleId") long vehicleId
    );
}
