package com.schoolbus.bookingservice.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface PaymentRefundLookupMapper {

    PaymentRefundRow selectByPaymentNumber(
            @Param("paymentNumber") String paymentNumber
    );

    int markRefundPending(
            @Param("id") long id,
            @Param("expectedVersion") long expectedVersion,
            @Param("reason") String reason,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    record PaymentRefundRow(
            long id,
            String paymentNumber,
            String bookingNumber,
            BigDecimal amount,
            String status,
            LocalDateTime completedAt,
            long version
    ) {
    }
}
