package com.schoolbus.transportquery.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TripSeatReadMapper {

    List<TripSeatStatusDataObject> selectSeatStatusesByTripId(
            @Param("tripId") Long tripId
    );
}
