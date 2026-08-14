package com.schoolbus.booking.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.booking.application.tripcancellation.TripCancellationBookingResult;
import com.schoolbus.booking.application.tripcancellation.TripCancellationBookingTransaction;
import com.schoolbus.booking.application.tripcancellation.TripCancellationRequestedEnvelope;
import com.schoolbus.booking.application.tripcancellation.TripCancellationRequestedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

@Component
@Profile("local")
public class TripCancellationRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(
            TripCancellationRequestedListener.class
    );

    private final TripCancellationBookingTransaction transaction;
    private final ObjectMapper objectMapper;

    public TripCancellationRequestedListener(
            TripCancellationBookingTransaction transaction,
            ObjectMapper objectMapper
    ) {
        this.transaction = Objects.requireNonNull(transaction);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @RabbitListener(
            queues = "${school-bus.messaging.trip-cancellation.requested-queue}"
    )
    public void consume(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            String eventId = message.getMessageProperties().getMessageId();
            TripCancellationRequestedMessage payload = objectMapper.readValue(
                    message.getBody(),
                    TripCancellationRequestedMessage.class
            );
            TripCancellationBookingResult result = transaction.process(
                    new TripCancellationRequestedEnvelope(eventId, payload)
            );
            channel.basicAck(tag, false);
            log.info(
                    "Trip cancellation bookings handled: tripId={}, cancelled={}, refunds={}, duplicate={}",
                    result.tripId(),
                    result.cancelledBookings(),
                    result.refundsRequested(),
                    result.duplicateEvent()
            );
        } catch (IOException | IllegalArgumentException exception) {
            log.error("Malformed trip cancellation request", exception);
            channel.basicReject(tag, false);
        } catch (RuntimeException exception) {
            log.error("Trip cancellation booking processing failed", exception);
            channel.basicNack(tag, false, true);
        }
    }
}
