package com.schoolbus.booking.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.schoolbus.booking.application.booking.BookingPaymentDeadlineEvent;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisBookingExpirationOutboxTest {

    private static final Instant NOW =
            Instant.parse("2026-08-11T00:00:00Z");

    @Test
    void shouldSerializeAndInsertDeadlineEvent() {
        BookingExpirationOutboxMapper mapper = mock(
                BookingExpirationOutboxMapper.class
        );
        when(mapper.insertEvent(
                anyString(),
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                any(),
                any(),
                any()
        )).thenReturn(1);
        MyBatisBookingExpirationOutbox outbox =
                new MyBatisBookingExpirationOutbox(
                        mapper,
                        new ObjectMapper()
                                .findAndRegisterModules()
                                .disable(
                                        SerializationFeature
                                                .WRITE_DATES_AS_TIMESTAMPS
                                )
                );

        outbox.append(new BookingPaymentDeadlineEvent(
                BookingId.of(5001L),
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                ),
                NOW.plusSeconds(900),
                NOW,
                0L
        ));

        ArgumentCaptor<String> payload =
                ArgumentCaptor.forClass(String.class);
        verify(mapper).insertEvent(
                anyString(),
                org.mockito.ArgumentMatchers.eq("5001"),
                org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.eq(
                        BookingPaymentDeadlineEvent.TYPE
                ),
                payload.capture(),
                any(),
                any(),
                any()
        );
        assertThat(payload.getValue())
                .contains("\"bookingId\":5001")
                .contains("\"expiresAt\":\"2026-08-11T00:15:00Z\"");
    }
}
