package com.schoolbus.booking.infrastructure.persistence.order;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BookingOrderMapper {

    int insertOrder(BookingOrderDataObject bookingOrder);

    BookingOrderDataObject selectById(@Param("id") Long id);

    BookingOrderDataObject selectByOrderNo(
            @Param("orderNo") String orderNo
    );

    BookingOrderDataObject selectByRequestNo(
            @Param("requestNo") String requestNo
    );

    boolean existsActiveByUserIdAndTripId(
            @Param("userId") Long userId,
            @Param("tripId") Long tripId
    );

    boolean existsActiveByTripId(@Param("tripId") Long tripId);

    int updateWithVersion(
            @Param("bookingOrder") BookingOrderDataObject bookingOrder,
            @Param("expectedVersion") Long expectedVersion
    );

    List<BookingOrderDataObject> selectExpiredPendingOrders(
            @Param("expiredAt") LocalDateTime expiredAt,
            @Param("limit") int limit
    );

    List<BookingOrderDataObject> selectByUserId(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("sortByCreatedAtAscending") boolean sortByCreatedAtAscending
    );

    long countByUserId(
            @Param("userId") Long userId,
            @Param("status") String status
    );
}
