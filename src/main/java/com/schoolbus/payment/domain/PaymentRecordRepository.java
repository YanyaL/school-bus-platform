package com.schoolbus.payment.domain;

import java.util.Optional;

public interface PaymentRecordRepository {

    PaymentRecord save(PaymentRecord paymentRecord);

    Optional<PaymentRecord> findByRequestNumber(
            PaymentRequestNumber requestNumber
    );

    Optional<PaymentRecord> findByPaymentNumber(
            PaymentNumber paymentNumber
    );
}
