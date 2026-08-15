package com.schoolbus.transportquery.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TripReadMapper {

    List<TripReadDataObject> selectBookableTrips(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    TripReadDataObject selectByTripNumber(
            @Param("tripNumber") String tripNumber
    );
}
