package com.schoolbus.paymentservice.application.refund;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRefundApplicationServiceTest {

    private final PaymentRefundTransaction transaction = mock(
            PaymentRefundTransaction.class
    );
    private final RefundGateway gateway = mock(RefundGateway.class);
    private final PaymentRefundApplicationService service =
            new PaymentRefundApplicationService(transaction, gateway);

    @Test
    void shouldNotCallProviderForConsumedEvent() {
        RefundMessageEnvelope envelope = envelope();
        when(transaction.isConsumed("event-1")).thenReturn(true);

        RefundProcessingResult result = service.process(envelope);

        assertThat(result.outcome())
                .isEqualTo(RefundProcessingOutcome.DUPLICATE_EVENT);
        verify(gateway, never()).refund(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldUsePaymentNumberAsProviderIdempotencyKey() {
        RefundMessageEnvelope envelope = envelope();
        RefundReceipt receipt = new RefundReceipt(
                "refund-001",
                Instant.parse("2026-08-10T10:00:00Z")
        );
        RefundProcessingResult expected = new RefundProcessingResult(
                RefundProcessingOutcome.REFUNDED,
                envelope.payload().paymentNumber(),
                "refund-001"
        );
        when(transaction.prepareRefund(envelope))
                .thenReturn(RefundPreparation.ready());
        when(gateway.refund(org.mockito.ArgumentMatchers.any()))
                .thenReturn(receipt);
        when(transaction.completeRefund(envelope, receipt))
                .thenReturn(expected);

        RefundProcessingResult result = service.process(envelope);

        ArgumentCaptor<RefundRequest> captor =
                ArgumentCaptor.forClass(RefundRequest.class);
        verify(gateway).refund(captor.capture());
        assertThat(captor.getValue().idempotencyKey())
                .isEqualTo(
                        "payment-refund:"
                                + envelope.payload().paymentNumber()
                );
        assertThat(result).isEqualTo(expected);
    }

    private RefundMessageEnvelope envelope() {
        return new RefundMessageEnvelope(
                "event-1",
                new PaymentRefundRequiredMessage(
                        "77777777-7777-7777-7777-777777777777",
                        "55555555-5555-5555-5555-555555555555",
                        new BigDecimal("5.50"),
                        "PAYMENT_WINDOW_EXPIRED",
                        Instant.parse("2026-08-10T09:50:00Z"),
                        Instant.parse("2026-08-10T09:55:00Z")
                )
        );
    }
}
