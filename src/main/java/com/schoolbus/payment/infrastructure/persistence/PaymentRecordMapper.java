package com.schoolbus.payment.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentRecordMapper {

    int insertPayment(PaymentRecordDataObject paymentRecord);

    PaymentRecordDataObject selectByRequestNo(
            @Param("requestNo") String requestNo
    );

    PaymentRecordDataObject selectByPaymentNo(
            @Param("paymentNo") String paymentNo
    );
}
