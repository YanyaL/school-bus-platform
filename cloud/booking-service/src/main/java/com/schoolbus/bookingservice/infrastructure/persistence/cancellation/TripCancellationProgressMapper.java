package com.schoolbus.bookingservice.infrastructure.persistence.cancellation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface TripCancellationProgressMapper {

    int insertProgress(
            @Param("tripId") long tripId,
            @Param("requestEventId") String requestEventId,
            @Param("pendingRefunds") int pendingRefunds,
            @Param("status") String status,
            @Param("createdAt") LocalDateTime createdAt
    );

    int decrementPendingRefund(
            @Param("tripId") long tripId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    String selectStatus(@Param("tripId") long tripId);
}
