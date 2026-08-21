package com.schoolbus.bookingservice.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.bookingservice.application.booking.BookingExpirationMessage;
import com.schoolbus.bookingservice.application.booking.BookingExpirationMessageApplicationService;
import com.schoolbus.bookingservice.application.booking.BookingExpirationMessageConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

@Component
@Profile("!test")
public class BookingExpirationListener {

    private static final Logger log = LoggerFactory.getLogger(
            BookingExpirationListener.class
    );

    private final BookingExpirationMessageApplicationService service;
    private final ObjectMapper objectMapper;

    public BookingExpirationListener(
            BookingExpirationMessageApplicationService service,
            ObjectMapper objectMapper
    ) {
        this.service = Objects.requireNonNull(
                service,
                "service must not be null"
        );
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
    }

    @RabbitListener(
            queues = "${school-bus.messaging.booking-expiration.processing-queue}"
    )
    public void consume(Message message, Channel channel)
            throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            BookingExpirationMessage payload = deserialize(message);
            boolean expired = service.process(payload);
            channel.basicAck(deliveryTag, false);
            log.info(
                    "Booking expiration event consumed: "
                            + "bookingId={}, expired={}",
                    payload.bookingId(),
                    expired
            );
        } catch (MalformedBookingExpirationMessageException
                 | BookingExpirationMessageConflictException exception) {
            log.error(
                    "Booking expiration event rejected as non-retryable",
                    exception
            );
            channel.basicReject(deliveryTag, false);
        } catch (RuntimeException exception) {
            log.error(
                    "Booking expiration processing failed; "
                            + "message will be dead-lettered and the "
                            + "database reconciliation job remains the fallback",
                    exception
            );
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private BookingExpirationMessage deserialize(Message message) {
        try {
            return objectMapper.readValue(
                    message.getBody(),
                    BookingExpirationMessage.class
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new MalformedBookingExpirationMessageException(
                    "invalid booking expiration message",
                    exception
            );
        }
    }
}
