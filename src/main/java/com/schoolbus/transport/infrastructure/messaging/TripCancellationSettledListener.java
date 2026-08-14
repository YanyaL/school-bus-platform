package com.schoolbus.transport.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.transport.application.trip.TripCancellationCompletionTransaction;
import com.schoolbus.transport.application.trip.TripCancellationSettledEnvelope;
import com.schoolbus.transport.application.trip.TripCancellationSettledMessage;
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
public class TripCancellationSettledListener {

    private static final Logger log = LoggerFactory.getLogger(
            TripCancellationSettledListener.class
    );

    private final TripCancellationCompletionTransaction transaction;
    private final ObjectMapper objectMapper;

    public TripCancellationSettledListener(
            TripCancellationCompletionTransaction transaction,
            ObjectMapper objectMapper
    ) {
        this.transaction = Objects.requireNonNull(transaction);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @RabbitListener(
            queues = "${school-bus.messaging.trip-cancellation.settled-queue}"
    )
    public void consume(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            String eventId = message.getMessageProperties().getMessageId();
            TripCancellationSettledMessage payload = objectMapper.readValue(
                    message.getBody(),
                    TripCancellationSettledMessage.class
            );
            boolean changed = transaction.complete(
                    new TripCancellationSettledEnvelope(eventId, payload)
            );
            channel.basicAck(tag, false);
            log.info(
                    "Trip cancellation finalized: tripId={}, changed={}",
                    payload.tripId(),
                    changed
            );
        } catch (IOException | IllegalArgumentException exception) {
            log.error("Malformed trip cancellation settled event", exception);
            channel.basicReject(tag, false);
        } catch (RuntimeException exception) {
            log.error("Trip cancellation finalization failed", exception);
            channel.basicNack(tag, false, true);
        }
    }
}
