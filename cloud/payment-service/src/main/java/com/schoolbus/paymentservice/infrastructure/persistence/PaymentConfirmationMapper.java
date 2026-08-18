package com.schoolbus.paymentservice.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface PaymentConfirmationMapper {

    @Select("""
            SELECT id, order_no, trip_id, seat_number, price_snapshot,
                   status, expires_at, version
            FROM booking_order
            WHERE order_no = #{orderNo}
            LIMIT 1
            FOR UPDATE
            """)
    BookingPaymentRow selectBookingForUpdate(String orderNo);

    @Select("""
            SELECT id, payment_no, request_no, order_no, amount,
                   status, failure_reason, completed_at
            FROM payment_record
            WHERE request_no = #{requestNo}
            LIMIT 1
            """)
    PaymentRecordRow selectByRequestNo(String requestNo);

    @Select("""
            SELECT id, payment_no, request_no, order_no, amount,
                   status, failure_reason, completed_at
            FROM payment_record
            WHERE payment_no = #{paymentNo}
            LIMIT 1
            """)
    PaymentRecordRow selectByPaymentNo(String paymentNo);

    @Update("""
            UPDATE transport_trip_seat
            SET status = 'SOLD', lock_expires_at = NULL,
                version = version + 1, updated_at = #{soldAt}
            WHERE trip_id = #{tripId}
              AND seat_number = #{seatNumber}
              AND status = 'LOCKED'
              AND locked_by_order_no = #{orderNo}
            """)
    int confirmSeatSold(
            @Param("tripId") long tripId,
            @Param("seatNumber") String seatNumber,
            @Param("orderNo") String orderNo,
            @Param("soldAt") LocalDateTime soldAt
    );

    @Update("""
            UPDATE booking_order
            SET status = 'PAID', payment_no = #{paymentNo},
                paid_at = #{paidAt}, version = version + 1,
                updated_at = #{updatedAt}
            WHERE id = #{id}
              AND status = 'PENDING_PAYMENT'
              AND version = #{expectedVersion}
            """)
    int confirmBookingPaid(
            @Param("id") long id,
            @Param("paymentNo") String paymentNo,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("expectedVersion") long expectedVersion
    );

    @Insert("""
            INSERT INTO payment_record (
                id, payment_no, request_no, order_no, amount,
                status, failure_reason, completed_at, version,
                created_at, updated_at
            ) VALUES (
                #{id}, #{paymentNo}, #{requestNo}, #{orderNo}, #{amount},
                #{status}, #{failureReason}, #{completedAt}, 0,
                #{createdAt}, #{createdAt}
            )
            """)
    int insertPayment(
            @Param("id") long id,
            @Param("paymentNo") String paymentNo,
            @Param("requestNo") String requestNo,
            @Param("orderNo") String orderNo,
            @Param("amount") BigDecimal amount,
            @Param("status") String status,
            @Param("failureReason") String failureReason,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("createdAt") LocalDateTime createdAt
    );

    @Insert("""
            INSERT INTO event_outbox (
                event_id, context_name, aggregate_type, aggregate_id,
                aggregate_version, event_type, payload, trace_id,
                status, retry_count, next_retry_at, occurred_at,
                created_at, published_at, version
            ) VALUES (
                #{eventId}, 'payment', 'PaymentRecord', #{aggregateId},
                0, 'PaymentRefundRequired', CAST(#{payload} AS JSON), #{traceId},
                'NEW', 0, NULL, #{occurredAt},
                #{occurredAt}, NULL, 0
            )
            """)
    int insertRefundOutbox(
            @Param("eventId") String eventId,
            @Param("aggregateId") String aggregateId,
            @Param("payload") String payload,
            @Param("traceId") String traceId,
            @Param("occurredAt") LocalDateTime occurredAt
    );
}
