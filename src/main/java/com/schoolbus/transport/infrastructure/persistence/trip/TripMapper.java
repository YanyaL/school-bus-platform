package com.schoolbus.transport.infrastructure.persistence.trip;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TripMapper {

    int insertTrip(TripDataObject trip);

    TripDataObject selectById(@Param("id") Long id);

    int updateWithVersion(
            @Param("trip") TripDataObject trip,
            @Param("expectedVersion") Long expectedVersion
    );
}
