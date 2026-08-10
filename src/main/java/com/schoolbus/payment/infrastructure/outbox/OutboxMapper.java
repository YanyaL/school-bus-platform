package com.schoolbus.payment.infrastructure.outbox;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxMapper {

    int insertEvent(
            @Param("eventId") String eventId,
            @Param("contextName") String contextName,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") String aggregateId,
            @Param("aggregateVersion") Long aggregateVersion,
            @Param("eventType") String eventType,
            @Param("payload") String payload,
            @Param("traceId") String traceId,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("createdAt") LocalDateTime createdAt
    );

    List<OutboxEventDataObject> selectRelayCandidates(
            @Param("contextName") String contextName,
            @Param("eventType") String eventType,
            @Param("readyAt") LocalDateTime readyAt,
            @Param("limit") int limit
    );

    int tryClaim(
            @Param("id") long id,
            @Param("expectedVersion") long expectedVersion,
            @Param("claimedUntil") LocalDateTime claimedUntil
    );

    int markPublished(
            @Param("id") long id,
            @Param("expectedVersion") long expectedVersion,
            @Param("publishedAt") LocalDateTime publishedAt
    );

    int markFailed(
            @Param("id") long id,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextRetryAt") LocalDateTime nextRetryAt
    );
}
