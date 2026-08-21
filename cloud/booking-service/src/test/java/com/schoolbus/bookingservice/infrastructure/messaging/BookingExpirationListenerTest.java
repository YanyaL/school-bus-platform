package com.schoolbus.bookingservice.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.bookingservice.application.booking.BookingExpirationMessage;
import com.schoolbus.bookingservice.application.booking.BookingExpirationMessageApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingExpirationListenerTest {

    private final BookingExpirationMessageApplicationService service = mock(
            BookingExpirationMessageApplicationService.class
    );
    private final Channel channel = mock(Channel.class);
    private final BookingExpirationListener listener =
            new BookingExpirationListener(
                    service,
                    new ObjectMapper().findAndRegisterModules()
            );

    @Test
    void shouldAckAfterSuccessfulIdempotentProcessing() throws Exception {
        when(service.process(any())).thenReturn(false);

        listener.consume(validMessage(), channel);

        verify(service).process(any(BookingExpirationMessage.class));
        verify(channel).basicAck(101L, false);
    }

    @Test
    void shouldRejectMalformedMessageWithoutRequeue() throws Exception {
        listener.consume(message("not-json"), channel);

        verify(channel).basicReject(101L, false);
    }

    @Test
    void shouldDeadLetterUnexpectedProcessingFailure() throws Exception {
        doThrow(new IllegalStateException("database unavailable"))
                .when(service).process(any());

        listener.consume(validMessage(), channel);

        verify(channel).basicNack(101L, false, false);
    }

    private Message validMessage() {
        return message(
                "{\"bookingId\":5001,"
                        + "\"bookingNumber\":"
                        + "\"55555555-5555-5555-5555-555555555555\","
                        + "\"expiresAt\":\"2026-08-11T00:15:00Z\","
                        + "\"occurredAt\":\"2026-08-11T00:00:00Z\"}"
        );
    }

    private Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(101L);
        return new Message(
                body.getBytes(StandardCharsets.UTF_8),
                properties
        );
    }
}
