package com.schoolbus.paymentservice.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface RefundBookingMapper {

    @Select("""
            SELECT id, order_no, trip_id, status
            FROM booking_order
            WHERE order_no = #{orderNo}
            LIMIT 1
            """)
    BookingRefundRow selectBookingForRefund(String orderNo);

    @Update("""
            UPDATE booking_order
            SET status = 'REFUNDED',
                version = version + 1,
                updated_at = #{refundedAt}
            WHERE id = #{id}
              AND status = 'REFUND_PENDING'
            """)
    int confirmBookingRefunded(
            @Param("id") long id,
            @Param("refundedAt") LocalDateTime refundedAt
    );

    @Update("""
            UPDATE booking_trip_cancellation_saga
            SET status = CASE
                    WHEN pending_refunds = 1 THEN 'SETTLED'
                    ELSE 'PROCESSING'
                END,
                pending_refunds = pending_refunds - 1,
                version = version + 1,
                updated_at = #{updatedAt}
            WHERE trip_id = #{tripId}
              AND status = 'PROCESSING'
              AND pending_refunds > 0
            """)
    int decrementTripCancellationPendingRefund(
            @Param("tripId") long tripId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Select("""
            SELECT status
            FROM booking_trip_cancellation_saga
            WHERE trip_id = #{tripId}
            """)
    String selectTripCancellationStatus(long tripId);

    @Insert("""
            INSERT INTO event_outbox (
                event_id, context_name, aggregate_type, aggregate_id,
                aggregate_version, event_type, payload, trace_id,
                status, retry_count, next_retry_at, occurred_at,
                created_at, published_at, version
            ) VALUES (
                #{eventId}, 'booking', 'TripCancellation', #{aggregateId},
                0, 'TripCancellationBookingsSettled', CAST(#{payload} AS JSON),
                #{traceId}, 'NEW', 0, NULL, #{occurredAt},
                #{occurredAt}, NULL, 0
            )
            """)
    int insertTripCancellationSettledOutbox(
            @Param("eventId") String eventId,
            @Param("aggregateId") String aggregateId,
            @Param("payload") String payload,
            @Param("traceId") String traceId,
            @Param("occurredAt") LocalDateTime occurredAt
    );

    record BookingRefundRow(
            long id,
            String orderNo,
            Long tripId,
            String status
    ) {
    }
}
