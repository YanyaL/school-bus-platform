package com.schoolbus.paymentservice.application;

import com.schoolbus.paymentservice.api.PaymentServiceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class PaymentConfirmationApplicationService {

    private final PaymentConfirmationTransaction transaction;

    public PaymentConfirmationApplicationService(
            PaymentConfirmationTransaction transaction
    ) {
        this.transaction = Objects.requireNonNull(transaction);
    }

    public ConfirmPaymentResult confirmPayment(
            ConfirmPaymentCommand command
    ) {
        try {
            return transaction.confirmOnce(command);
        } catch (DataIntegrityViolationException duplicate) {
            return transaction.resolveDuplicate(command);
        } catch (OptimisticLockingFailureException conflict) {
            throw new PaymentServiceException(
                    "PAYMENT_CONCURRENCY_CONFLICT",
                    "payment was modified by another request",
                    HttpStatus.CONFLICT
            );
        }
    }
}
