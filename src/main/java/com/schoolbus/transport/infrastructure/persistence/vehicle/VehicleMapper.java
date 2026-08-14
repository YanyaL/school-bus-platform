package com.schoolbus.transport.infrastructure.persistence.vehicle;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface VehicleMapper {

    int insertVehicle(VehicleDataObject vehicle);

    int updateWithVersion(
            @Param("vehicle") VehicleDataObject vehicle,
            @Param("expectedVersion") long expectedVersion
    );

    VehicleDataObject selectById(@Param("id") long id);

    VehicleDataObject selectByIdForUpdate(@Param("id") long id);

    VehicleDataObject selectByVehicleNumber(
            @Param("vehicleNumber") String vehicleNumber
    );

    VehicleDataObject selectByLicensePlate(
            @Param("licensePlate") String licensePlate
    );

    List<VehicleDataObject> selectAll(
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int count(@Param("status") String status);
}
