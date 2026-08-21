package com.schoolbus.paymentservice.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentRecordMapper {

    int insertPayment(PaymentRecordDataObject paymentRecord);

    int updatePayment(
            @Param("paymentRecord") PaymentRecordDataObject paymentRecord,
            @Param("expectedVersion") long expectedVersion
    );

    PaymentRecordDataObject selectByRequestNo(
            @Param("requestNo") String requestNo
    );

    PaymentRecordDataObject selectByPaymentNo(
            @Param("paymentNo") String paymentNo
    );
}
