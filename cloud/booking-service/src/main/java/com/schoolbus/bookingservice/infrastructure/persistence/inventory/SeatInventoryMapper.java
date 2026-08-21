package com.schoolbus.bookingservice.infrastructure.persistence.inventory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SeatInventoryMapper {

    int insertInventory(SeatInventoryDataObject inventory);

    SeatInventoryDataObject selectByTripId(
            @Param("tripId") Long tripId
    );

    int updateWithVersion(
            @Param("inventory") SeatInventoryDataObject inventory,
            @Param("expectedVersion") Long expectedVersion
    );
}
