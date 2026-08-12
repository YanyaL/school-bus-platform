package com.schoolbus.transport.infrastructure.persistence.seat;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TripSeatQueryMapper {

    List<TripSeatStatusDataObject> selectSeatStatusesByTripId(
            @Param("tripId") Long tripId
    );
}
