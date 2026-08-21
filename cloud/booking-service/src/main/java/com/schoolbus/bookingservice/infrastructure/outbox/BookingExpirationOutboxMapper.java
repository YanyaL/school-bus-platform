package com.schoolbus.bookingservice.infrastructure.outbox;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface BookingExpirationOutboxMapper {

    int insertEvent(
            @Param("eventId") String eventId,
            @Param("aggregateId") String aggregateId,
            @Param("aggregateVersion") long aggregateVersion,
            @Param("eventType") String eventType,
            @Param("payload") String payload,
            @Param("traceId") String traceId,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("createdAt") LocalDateTime createdAt
    );
}
