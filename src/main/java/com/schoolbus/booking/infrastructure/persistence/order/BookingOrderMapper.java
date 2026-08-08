package com.schoolbus.booking.infrastructure.persistence.order;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BookingOrderMapper {

    int insertOrder(BookingOrderDataObject bookingOrder);

    BookingOrderDataObject selectById(@Param("id") Long id);

    boolean existsActiveByUserIdAndTripId(
            @Param("userId") Long userId,
            @Param("tripId") Long tripId
    );

    int updateWithVersion(
            @Param("bookingOrder") BookingOrderDataObject bookingOrder,
            @Param("expectedVersion") Long expectedVersion
    );
}
