package com.schoolbus.paymentservice.application.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.paymentservice.domain.BookingNumber;
import com.schoolbus.paymentservice.infrastructure.booking.OutboxRefundedBookingAdapter;
import com.schoolbus.paymentservice.infrastructure.outbox.OutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRefundedBookingAdapterTest {

    private static final Instant NOW =
            Instant.parse("2026-08-21T12:00:00Z");

    private OutboxMapper outboxMapper;
    private OutboxRefundedBookingAdapter adapter;

    @BeforeEach
    void setUp() {
        outboxMapper = mock(OutboxMapper.class);
        adapter = new OutboxRefundedBookingAdapter(
                outboxMapper,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void shouldAppendPaymentRefundedOutboxEvent() {
        when(outboxMapper.insertEvent(
                any(), any(), any(), any(), anyLong(), any(), any(),
                isNull(), any(), any()
        )).thenReturn(1);

        adapter.markRefunded(new PaymentRefundedCommand(
                "77777777-7777-7777-7777-777777777777",
                BookingNumber.of("55555555-5555-5555-5555-555555555555"),
                "refund-001",
                "TRIP_CANCELLED",
                NOW,
                NOW
        ));

        ArgumentCaptor<String> eventType =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload =
                ArgumentCaptor.forClass(String.class);
        verify(outboxMapper).insertEvent(
                any(),
                eq(OutboxRefundedBookingAdapter.CONTEXT_NAME),
                eq(OutboxRefundedBookingAdapter.AGGREGATE_TYPE),
                eq("77777777-7777-7777-7777-777777777777"),
                eq(0L),
                eventType.capture(),
                payload.capture(),
                isNull(),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        );
        assertThat(eventType.getValue())
                .isEqualTo(OutboxRefundedBookingAdapter.EVENT_TYPE);
        assertThat(payload.getValue()).contains("TRIP_CANCELLED");
        assertThat(payload.getValue()).contains("refund-001");
    }
}
