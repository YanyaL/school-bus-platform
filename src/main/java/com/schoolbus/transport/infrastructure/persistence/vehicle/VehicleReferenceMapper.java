package com.schoolbus.transport.infrastructure.persistence.vehicle;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VehicleReferenceMapper {

    VehicleReferenceDataObject selectByVehicleNo(
            @Param("vehicleNo") String vehicleNo
    );

    VehicleReferenceDataObject selectById(
            @Param("id") Long id
    );

    List<String> selectSeatNumbersByVehicleId(
            @Param("vehicleId") Long vehicleId
    );
}
