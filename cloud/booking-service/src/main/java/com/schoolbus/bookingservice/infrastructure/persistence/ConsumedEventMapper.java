package com.schoolbus.bookingservice.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ConsumedEventMapper {

    int exists(
            @Param("consumerName") String consumerName,
            @Param("eventId") String eventId
    );

    int insertIfAbsent(
            @Param("consumerName") String consumerName,
            @Param("eventId") String eventId,
            @Param("consumedAt") LocalDateTime consumedAt
    );
}
