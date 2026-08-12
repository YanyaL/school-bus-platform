package com.schoolbus.transport.infrastructure.persistence.trip;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TripMapper {

    int insertTrip(TripDataObject trip);

    TripDataObject selectById(@Param("id") Long id);

    TripDataObject selectByTripNumber(
            @Param("tripNumber") String tripNumber
    );

    int countActiveTripsForVehicleDeparture(
            @Param("vehicleId") Long vehicleId,
            @Param("excludingTripId") Long excludingTripId,
            @Param("departureTime") LocalDateTime departureTime
    );

    List<TripDataObject> selectBookableTrips(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    List<TripDataObject> selectDueOpenTripsForClosing(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    List<TripDataObject> selectDueClosedTripsForDeparture(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    int updateWithVersion(
            @Param("trip") TripDataObject trip,
            @Param("expectedVersion") Long expectedVersion
    );
}
