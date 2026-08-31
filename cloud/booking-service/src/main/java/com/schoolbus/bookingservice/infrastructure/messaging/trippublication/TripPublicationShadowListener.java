package com.schoolbus.bookingservice.infrastructure.messaging.trippublication;

import com.schoolbus.bookingservice.application.trippublication.TripPublicationShadowTransaction;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class TripPublicationShadowListener {
    private static final Logger log = LoggerFactory.getLogger(TripPublicationShadowListener.class);
    private final TripPublicationMessageDecoder decoder;
    private final TripPublicationShadowTransaction transaction;
    private final MeterRegistry metrics;

    public TripPublicationShadowListener(TripPublicationMessageDecoder decoder, TripPublicationShadowTransaction transaction, MeterRegistry metrics) {
        this.decoder = decoder;
        this.transaction = transaction;
        this.metrics = metrics;
    }

    @RabbitListener(queues = "${school-bus.booking.trip-publication-shadow.queue:schoolbus.booking.trip-published.shadow}",
            containerFactory = "tripPublicationShadowContainerFactory")
    public void consume(Message message) {
        var event = decoder.decode(message);
        var outcome = transaction.observe(event);
        metrics.counter("schoolbus.booking.trip_publication.shadow", "outcome", outcome.name()).increment();
        // AUTO acknowledgement happens only after this method returns and the transaction proxy has committed.
        log.info("TripPublished shadow: eventId={}, outcome={}", event.eventId(), outcome);
    }
}
