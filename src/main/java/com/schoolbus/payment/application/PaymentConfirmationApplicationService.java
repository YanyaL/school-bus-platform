package com.schoolbus.payment.application;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Profile("!test")
public class PaymentConfirmationApplicationService {

    private final PaymentConfirmationTransaction transaction;

    public PaymentConfirmationApplicationService(
            PaymentConfirmationTransaction transaction
    ) {
        this.transaction = Objects.requireNonNull(
                transaction,
                "transaction must not be null"
        );
    }

    public ConfirmPaymentResult confirmPayment(
            ConfirmPaymentCommand command
    ) {
        try {
            return transaction.confirmOnce(command);
        } catch (DataIntegrityViolationException duplicate) {
            return transaction.resolveDuplicate(command);
        } catch (OptimisticLockingFailureException conflict) {
            throw new PaymentConcurrencyException(conflict);
        }
    }
}
